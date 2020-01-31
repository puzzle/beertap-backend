package ch.puzzle.lightning.minizeus.lnd.boundary;

import ch.puzzle.lightning.minizeus.SslUtils;
import ch.puzzle.lightning.minizeus.invoices.entity.Invoice;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceCreated;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceUpdated;
import ch.puzzle.lightning.minizeus.lightning.boundary.LightningClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.*;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static ch.puzzle.lightning.minizeus.conversions.boundary.ConvertService.bytesToHex;

@ApplicationScoped
public class LndRestClient implements LightningClient {

    private static final Logger LOG = Logger.getLogger(LndRestClient.class.getName());

    @Inject
    Event<InvoiceUpdated> invoiceUpdateEvent;
    @Inject
    Event<InvoiceCreated> invoiceCreatedEvent;

    @Inject
    @ConfigProperty(name = "lnd.url")
    Optional<String> url;

    @Inject
    @ConfigProperty(name = "lnd.config.macaroon.readonly")
    Optional<String> readonlyMacaroonHex;

    @Inject
    @ConfigProperty(name = "lnd.config.macaroon.invoice")
    Optional<String> invoiceMacaroonHex;

    @Inject
    @ConfigProperty(name = "lnd.config.cert.path")
    Optional<String> certPath;
    private Client client;

    private synchronized WebTarget getLndTarget() {
        return ClientBuilder
                .newBuilder()
                .sslContext(getSslContext())
                .build().target(url.orElseThrow());
    }

    private SSLContext getSslContext() {
        return SslUtils.getSslContextForCertificateFile(certPath.orElseThrow());
    }

    @Override
    public Invoice generateInvoice(long amount, String memo) {
        long expiry = Instant.now().getEpochSecond() + getInvoiceExpiry();
        JsonObject request = Json.createObjectBuilder()
                .add("value", amount)
                .add("memo", memo)
                .add("expiry", getInvoiceExpiry())
                .build();

        JsonObject response = addInvoiceHeaders(getLndTarget().path("v1").path("invoices"))
                .post(Entity.json(request), JsonObject.class);

        String id = bytesToHex(response.getString("r_hash").getBytes());

        InvoiceCreated invoiceCreated = new InvoiceCreated();
        invoiceCreated.id = id;
        invoiceCreated.expiry = expiry;
        invoiceCreatedEvent.fireAsync(invoiceCreated);

        Invoice invoice = new Invoice();
        invoice.paymentRequest = response.getString("payment_request");
        invoice.memo = memo;
        invoice.settled = false;
        invoice.id = id;

        return invoice;
    }

    @Override
    public JsonObject getInfo() {
        return addReadonlyHeaders(getLndTarget().path("v1").path("getinfo")).get(JsonObject.class);
    }

    @Override
    public void startInvoiceListen() {
        subscribeToInvoices();
    }


    private void accept(JsonObject invoice) {
        InvoiceUpdated event = new InvoiceUpdated();
        event.id = bytesToHex(invoice.getString("r_hash").getBytes());
        event.settled = invoice.getBoolean("settled", false);
        event.memo = invoice.getString("memo", "");
        if (event.settled) {
            invoiceUpdateEvent.fireAsync(event);
        }
    }

    private void subscribeToInvoices() {
        System.out.println("subscribing to lnd invoices");
        ForkJoinPool.commonPool().execute(() -> {
            InputStream inputStream = addReadonlyHeaders(getLndTarget().path("v1").path("invoices").path("subscribe"))
                    .get(InputStream.class);
            Jsonb jsonb = JsonbBuilder.create();
            new BufferedReader(new InputStreamReader(inputStream,
                    StandardCharsets.UTF_8))
                    .lines()
                    .map(s -> jsonb.fromJson(s, JsonObject.class))
                    .map(jsonObject -> jsonObject.getJsonObject("result"))
                    .forEach(this::accept);
        });
    }

    @Override
    public boolean isConfigured() {
        return isConfigured(url)
                && isConfigured(readonlyMacaroonHex)
                && isConfigured(invoiceMacaroonHex)
                && isConfigured(certPath);
    }

    private boolean isConfigured(Optional<String> param) {
        return param.filter(Predicate.not(String::isEmpty)).isPresent();
    }


    private Invocation.Builder addInvoiceHeaders(WebTarget target) {
        return addHeaders(registerAuthFilter(target, invoiceMacaroonHex));
    }

    private WebTarget registerAuthFilter(WebTarget target, Optional<String> macaroon) {
        return target.register(new LndAuthHeaderFilter(macaroon.orElseThrow()));
    }

    private Invocation.Builder addReadonlyHeaders(WebTarget target) {
        return addHeaders(registerAuthFilter(target, readonlyMacaroonHex));
    }

    private Invocation.Builder addHeaders(WebTarget target) {
        return target
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.CONTENT_TYPE, "application/json");
    }

}
