package ch.puzzle.lightning.minizeus.opennode.boundary;

import ch.puzzle.lightning.minizeus.btcpay.boundary.BtcPayClient;
import ch.puzzle.lightning.minizeus.invoices.boundary.InvoiceCache;
import ch.puzzle.lightning.minizeus.invoices.entity.Invoice;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceCreated;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceUpdated;
import ch.puzzle.lightning.minizeus.invoices.entity.ZeusInternalException;
import ch.puzzle.lightning.minizeus.lightning.boundary.InvoiceStatusPollingClient;
import ch.puzzle.lightning.minizeus.lightning.boundary.LightningClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.logging.Logger;

@ApplicationScoped
public class OpenNodeClient implements LightningClient {

    private static final Logger LOG = Logger.getLogger(OpenNodeClient.class.getSimpleName());

    @Inject
    @ConfigProperty(name = "opennode.api.token")
    Optional<String> openNodeApiToken;

    @Inject
    @ConfigProperty(name = "opennode.api.uri")
    Optional<String> openNodeApiUri;

    @Inject
    Event<InvoiceUpdated> invoiceUpdateEvent;

    @Inject
    Event<InvoiceCreated> invoiceCreatedEvent;

    @Inject
    InvoiceStatusPollingClient client;

    @Inject
    Event<OpenNodeSchedule> invoiceCheckSchedule;

    @Inject
    InvoiceCache invoiceCache;

    private ScheduledExecutorService executor;
    private WebTarget openNodeTarget;
    volatile private boolean listening;

    @PostConstruct
    public void init() {
        if (isConfigured()) {
            openNodeTarget = ClientBuilder
                    .newClient()
                    .target(openNodeApiUri.orElseThrow())
                    .path("v1");
        }
    }

    public Invoice generateInvoice(long amount, String memo) {
        JsonObject charge = createAddInvoiceRequest(amount, memo);
        JsonObject response = createInvoice(charge).getJsonObject("data");
        fireInvoiceCreatedEvent(response);
        return invoiceFromCreateInvoiceResponse(response);
    }


    private Invoice invoiceFromCreateInvoiceResponse(JsonObject response) {
        Invoice invoice = new Invoice();
        invoice.paymentRequest = response.getJsonObject("lightning_invoice").getString("payreq");
        invoice.id = response.getString("id");
        invoice.memo = response.getString("description");
        return invoice;
    }

    private JsonObject createAddInvoiceRequest(long amount, String memo) {
        return Json.createObjectBuilder()
                .add("amount", amount)
                .add("description", memo)
                .build();
    }

    private void fireInvoiceCreatedEvent(JsonObject response) {
        InvoiceCreated invoiceCreated = new InvoiceCreated();
        invoiceCreated.id = response.getString("id");
        invoiceCreated.expiry = response.getJsonObject("lightning_invoice").getJsonNumber("expires_at").longValue();
        invoiceCreatedEvent.fireAsync(invoiceCreated);
    }

    public JsonObject generateInvoiceWithCallback(String orderId, long amount, String memo, String redirectUrl) {
        JsonObject charge = createAddInvoiceRequestWithRedirect(orderId, amount, memo, redirectUrl);
        return createInvoice(charge).getJsonObject("data");
    }

    private JsonObject createAddInvoiceRequestWithRedirect(String orderId, long amount, String memo, String redirectUrl) {
        return Json.createObjectBuilder()
                .add("amount", amount)
                .add("description", memo)
                .add("order_id", orderId)
                .add("success_url", redirectUrl)
                .build();
    }


    public JsonObject getInfo() {
        return getChargesBuilder().get(JsonObject.class);
    }

    private JsonObject createInvoice(JsonObject charge) {
        return getChargesBuilder().post(Entity.json(charge), JsonObject.class);

    }

    public boolean isConfigured() {
        return openNodeApiToken.filter(Predicate.not(String::isEmpty)).isPresent()
                && openNodeApiUri.filter(Predicate.not(String::isEmpty)).isPresent();
    }


    private void pollForCompletion() {
        CompletableFuture<Boolean> completionFuture = new CompletableFuture<>();
        final ScheduledFuture<?> checkFuture = executor.scheduleAtFixedRate(() -> {
            if (!this.listening) {
                completionFuture.complete(true);
            }
            invoiceCheckSchedule.fire(new OpenNodeSchedule());
        }, 0, 1, TimeUnit.SECONDS);
        completionFuture.whenComplete((result, thrown) -> {
            checkFuture.cancel(true);
        });
    }

    void checkInvoices(@Observes OpenNodeSchedule schedule) {
        LOG.info("Checking Opennode invoices");
        for (String invoiceId : this.invoiceCache.getPendingInvoices()) {
            checkInvoiceStatus(invoiceId);
        }
        try {
            TimeUnit.SECONDS.sleep(1L);
        } catch (InterruptedException e) {
            LOG.severe(e.getMessage());
            throw new RuntimeException(e);
        }
    }


    @Override
    public void startInvoiceListen() {
        System.out.println("Check OpenNode invoice");
        if (isConfigured()) {
            executor = Executors.newSingleThreadScheduledExecutor();
            System.out.println("Starting OpenNode invoice subscription");
            this.listening = true;
            LOG.info("Starting OpenNode invoice subscription");
            pollForCompletion();
        } else {
            throw new ZeusInternalException("BtcPay is not configured");
        }
    }

    public JsonObject getInvoice(String invoiceId) {
        return getChargeBuilder(invoiceId).get(JsonObject.class).getJsonObject("data");
    }

    private void checkInvoiceStatus(String invoiceId) {
        JsonObject charge = getInvoice(invoiceId);
        InvoiceUpdated invoiceUpdated = new InvoiceUpdated();
        invoiceUpdated.id = charge.getString("id");
        invoiceUpdated.settled = "paid".equals(charge.getString("status"));
        invoiceUpdated.memo = charge.getString("description");
        if (invoiceUpdated.settled) {
            LOG.info("Sending update event");
            invoiceUpdateEvent.fireAsync(invoiceUpdated);
        }
    }


    private Invocation.Builder getChargesBuilder() {
        return addHeaders(openNodeTarget.path("charges"));
    }

    private Invocation.Builder getChargeBuilder(String id) {
        return addHeaders(openNodeTarget.path("charge").path(id));
    }


    private Invocation.Builder addHeaders(WebTarget target) {

        return target
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.AUTHORIZATION, openNodeApiToken.orElseThrow());
    }

    private class OpenNodeSchedule {
    }
}
