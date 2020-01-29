package ch.puzzle.lightning.minizeus.lnd.boundary;

import ch.puzzle.lightning.minizeus.lightning.boundary.LightningClient;
import ch.puzzle.lightning.minizeus.invoices.entity.*;
import io.grpc.Status;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import io.grpc.stub.StreamObserver;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.lightningj.lnd.wrapper.*;
import org.lightningj.lnd.wrapper.message.AddInvoiceResponse;
import org.lightningj.lnd.wrapper.message.Invoice;
import org.lightningj.lnd.wrapper.message.InvoiceSubscription;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Destroyed;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.net.ssl.SSLException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static ch.puzzle.lightning.minizeus.conversions.boundary.ConvertService.bytesToHex;

@ApplicationScoped
public class LndClient implements StreamObserver<Invoice>, LightningClient {

    private static final Logger LOG = Logger.getLogger(LndClient.class.getName());
    private static final long CONNECTION_RETRY_TIMEOUT = 10000;
    private static final long NODE_LOCKED_RETRY_TIMEOUT = 30000;

    private SynchronousLndAPI readonlySyncAPI;
    private SynchronousLndAPI invoiceSyncAPI;
    private AsynchronousLndAPI readonlyAsyncAPI;

    @Inject
    Event<InvoiceUpdated> invoiceUpdateEvent;
    @Inject
    Event<InvoiceCreated> invoiceCreatedEvent;

    @Inject
    @ConfigProperty(name = "lnd.host")
    Optional<String> host;

    @Inject
    @ConfigProperty(name = "lnd.port")
    Integer port;

    @Inject
    @ConfigProperty(name = "lnd.config.macaroon-readonly.path")
    String readonlyMacaroonPath;

    @Inject
    @ConfigProperty(name = "lnd.config.macaroon-invoice.path")
    String invoiceMacaroonPath;

    @Inject
    @ConfigProperty(name = "lnd.config.cert.path")
    String certPath;

    @Inject
    @ConfigProperty(name = "lnd.config.base-path")
    String basePath;

    private void subscribeToInvoices() throws SSLException, StatusException, ValidationException {
        if (isConfigured()) {
            LOG.info("Starting invoice subscription to lnd");
            InvoiceSubscription invoiceSubscription = new InvoiceSubscription();
            getAsyncReadonlyApi().subscribeInvoices(invoiceSubscription, this);
        } else {
            throw new ZeusInternalException("Lnd is not configured");
        }
    }

    @Retry(retryOn = {RequestStatusException.class, SSLException.class}, delay = 1L, delayUnit = ChronoUnit.SECONDS)
    public ch.puzzle.lightning.minizeus.invoices.entity.Invoice generateInvoice(long amount, String memo) {
        Invoice invoiceRequest = new Invoice();
        invoiceRequest.setValue(amount);
        invoiceRequest.setMemo(memo);
        invoiceRequest.setExpiry(getInvoiceExpiry());
        try {
            AddInvoiceResponse response = getSyncInvoiceApi().addInvoice(invoiceRequest);
            ch.puzzle.lightning.minizeus.invoices.entity.Invoice invoice =
                    ch.puzzle.lightning.minizeus.invoices.entity.Invoice.fromAddInvoice(response);
            invoice.memo = memo;
            invoice.expiry = getInvoiceExpiry();
            invoiceCreatedEvent.fireAsync(new InvoiceCreated(invoice));
            return invoice;
        } catch (StatusException e) {
            closeSyncInvoiceApi();
            e.printStackTrace();
            throw new RequestStatusException(e);
        } catch (ValidationException e) {
            e.printStackTrace();
            throw new LndValidationException(e);
        } catch (SSLException e) {
            e.printStackTrace();
            closeSyncInvoiceApi();
            throw new ZeusInternalException(e);
        }
    }

    @Retry(delay = 1L, delayUnit = ChronoUnit.SECONDS)
    public JsonObject getInfo() {
        LOG.info("getInfo called");
        try {
            return getSyncReadonlyApi().getInfo().toJson().build();
        } catch (StatusException | ValidationException | IOException e) {
            LOG.warning("getInfo call failed, retrying with fresh api");
            closeSyncReadonlyApi();
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isConfigured() {
        return host.filter(Predicate.not(String::isEmpty)).isPresent() &&
                port != null;
    }

    @Override
    public void onNext(Invoice invoice) {
        String invoiceHex = bytesToHex(invoice.getRHash());
        LOG.info("Received update on subscription for " + invoiceHex);
        invoiceUpdateEvent.fireAsync(new InvoiceUpdated(
                ch.puzzle.lightning.minizeus.invoices.entity.Invoice.fromLndInvoice(invoice)));
    }

    @Override
    @Retry(delay = CONNECTION_RETRY_TIMEOUT, maxRetries = 10)
    public void onError(Throwable t) {
        try {
            if (t instanceof ServerSideException && ((ServerSideException) t).getStatus().getCode() == Status.Code.UNIMPLEMENTED) {
                LOG.severe("It seems the lightning node is locked! Please unlock it. Will try again in " + NODE_LOCKED_RETRY_TIMEOUT / 1000 + " seconds.");
                Thread.sleep(NODE_LOCKED_RETRY_TIMEOUT);
            } else {
                LOG.severe("Subscription for listening to invoices failed with message '" + t.getMessage() + "'! Will try again in " + CONNECTION_RETRY_TIMEOUT / 1000 + " seconds.");
                Thread.sleep(CONNECTION_RETRY_TIMEOUT);
            }

            // after waiting an appropriate amount of time, we try again...
            try {
                closeAsyncInvoiceApi();
                subscribeToInvoices();
            } catch (StatusException | ValidationException | SSLException e) {
                LOG.severe("Couldn't subscribe to invoices! sleeping for 5 seconds");
                Thread.sleep(CONNECTION_RETRY_TIMEOUT);
                onError(e);
            }
        } catch (InterruptedException e1) {
            LOG.severe("woke up from sleep, exiting loop");
        }
    }

    @Override
    public void onCompleted() {
        LOG.info("Subscription for listening to invoices completed.");
    }

    @Override
    public void startInvoiceListen() {
        try {
            subscribeToInvoices();
        } catch (SSLException | StatusException | ValidationException e) {
            onError(e);
        }
    }

    public void destroy(@Observes @Destroyed(ApplicationScoped.class) Object init) {
        closeApis();
    }

    private void closeApis() {
        closeAsyncInvoiceApi();
        closeSyncReadonlyApi();
        closeSyncInvoiceApi();
    }


    private void closeAsyncInvoiceApi() {
        if (readonlyAsyncAPI != null) {
            try {
                readonlyAsyncAPI.close();
            } catch (StatusException e) {
                LOG.severe("Couldn't close async api");
            } finally {
                readonlyAsyncAPI = null;
            }
        }
    }

    private void closeSyncReadonlyApi() {
        if (readonlySyncAPI != null) {
            try {
                readonlySyncAPI.close();
            } catch (StatusException e) {
                LOG.severe("Couldn't close async api");
            } finally {
                readonlySyncAPI = null;
            }
        }
    }

    private void closeSyncInvoiceApi() {
        if (invoiceSyncAPI != null) {
            try {
                invoiceSyncAPI.close();
            } catch (StatusException e) {
                LOG.severe("Couldn't close async api");
            } finally {
                invoiceSyncAPI = null;
            }
        }
    }


    private SynchronousLndAPI getSyncReadonlyApi() throws IOException {
        if (readonlySyncAPI == null) {
            readonlySyncAPI = new SynchronousLndAPI(
                    host.orElseThrow(),
                    port,
                    getSslContext(),
                    getMacaroonContext(readonlyMacaroonPath)
            );
        }
        return readonlySyncAPI;
    }


    private SynchronousLndAPI getSyncInvoiceApi() throws SSLException {
        if (invoiceSyncAPI == null) {
            invoiceSyncAPI = new SynchronousLndAPI(
                    host.orElseThrow(),
                    port,
                    getSslContext(),
                    getMacaroonContext(invoiceMacaroonPath)
            );
        }
        return invoiceSyncAPI;
    }

    private AsynchronousLndAPI getAsyncReadonlyApi() throws SSLException {
        if (readonlyAsyncAPI == null) {
            readonlyAsyncAPI = new AsynchronousLndAPI(
                    host.orElseThrow(),
                    port,
                    getSslContext(),
                    getMacaroonContext(readonlyMacaroonPath)
            );
        }
        return readonlyAsyncAPI;
    }

    private SslContext getSslContext() throws SSLException {
        return GrpcSslContexts
                .configure(SslContextBuilder.forClient(), SslProvider.OPENSSL)
                .trustManager(Paths.get(basePath + File.separator + certPath).toFile())
                .build();
    }

    private MacaroonContext getMacaroonContext(String macaroonPath) {
        try {
            return new StaticFileMacaroonContext(Paths.get(basePath + File.separator + macaroonPath).toFile());
        } catch (ClientSideException e) {
            throw new RuntimeException(e);
        }
    }
}
