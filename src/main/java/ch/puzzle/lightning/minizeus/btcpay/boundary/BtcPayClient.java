package ch.puzzle.lightning.minizeus.btcpay.boundary;

import ch.puzzle.lightning.minizeus.invoices.boundary.InvoiceCache;
import ch.puzzle.lightning.minizeus.invoices.entity.Invoice;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceCreated;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceUpdated;
import ch.puzzle.lightning.minizeus.invoices.entity.ZeusInternalException;
import ch.puzzle.lightning.minizeus.lightning.boundary.LightningClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Destroyed;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.bind.JsonbBuilder;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.logging.Logger;

@ApplicationScoped
public class BtcPayClient implements LightningClient {

    private static final Logger LOG = Logger.getLogger(BtcPayClient.class.getSimpleName());

    @Inject
    @ConfigProperty(name = "btcpay.api.token")
    Optional<String> btcPayApiToken;

    @Inject
    @ConfigProperty(name = "btcpay.api.uri")
    Optional<String> openNodeApiUri;

    @Inject
    Event<InvoiceUpdated> invoiceUpdateEvent;

    @Inject
    Event<InvoiceCreated> invoiceCreatedEvent;

    @Inject
    Event<BtcPaySchedule> invoiceCheckSchedule;

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
                    .target(openNodeApiUri.orElseThrow());
        }
    }

    public Invoice generateInvoice(long amount, String memo) {
        JsonObject btcPayInvoice = createAddInvoiceRequest(amount, memo);
        JsonObject response = createInvoice(btcPayInvoice);
        fireInvoiceCreated(response);
        return invoiceFromCreateInvoiceResponse(response);
    }

    private JsonObject createAddInvoiceRequest(long amount, String memo) {
        return Json.createObjectBuilder()
                .add("price", amount)
                .add("currency", "sats")
                .add("itemDesc", memo)
                .build();
    }

    private void fireInvoiceCreated(JsonObject response) {
        InvoiceCreated invoiceCreated = new InvoiceCreated();
        invoiceCreated.id = response.getString("id");
        invoiceCreated.expiry = response.getJsonNumber("expirationTime").longValue() / 1000L;
        invoiceCreatedEvent.fireAsync(invoiceCreated);
    }

    private Invoice invoiceFromCreateInvoiceResponse(JsonObject response) {
        Invoice invoice = new Invoice();
        invoice.paymentRequest = response.getJsonObject("addresses").getString("BTC_LightningLike");
        invoice.id = response.getString("id");
        invoice.memo = response.getString("itemDesc");
        return invoice;
    }

    public JsonObject generateInvoiceWithRedirect(String orderId, long amount, String memo, String callbackUrl) {
        JsonObject btcPayInvoice = createAddInvoiceRequestWithRedirect(orderId, amount, memo, callbackUrl);
        return createInvoice(btcPayInvoice);
    }

    private JsonObject createAddInvoiceRequestWithRedirect(String orderId, long amount, String memo, String redirectUrl) {
        return Json.createObjectBuilder()
                .add("orderId", orderId)
                .add("price", amount)
                .add("currency", "sats")
                .add("itemDesc", memo)
                .add("redirectURL", redirectUrl)
                .build();
    }


    public JsonObject getInfo() {
        return getInvoicesBuilder().get(JsonObject.class);
    }

    private JsonObject createInvoice(JsonObject charge) {
        return getInvoicesBuilder().post(Entity.json(charge), JsonObject.class).getJsonObject("data");

    }

    public boolean isConfigured() {
        return btcPayApiToken.filter(Predicate.not(String::isEmpty)).isPresent()
                && openNodeApiUri.filter(Predicate.not(String::isEmpty)).isPresent();
    }


    @Override
    public void startInvoiceListen() {
        System.out.println("Check btcpay invoice");
        if (isConfigured()) {
            executor = Executors.newSingleThreadScheduledExecutor();
            System.out.println("Starting BtcPay invoice subscription");
            this.listening = true;
            LOG.info("Starting BtcPay invoice subscription");
            pollForCompletion();
        } else {
            throw new ZeusInternalException("BtcPay is not configured");
        }
    }

    public void destroy(@Observes @Destroyed(ApplicationScoped.class) Object init) {
        this.listening = false;
    }


    private void pollForCompletion() {
        CompletableFuture<Boolean> completionFuture = new CompletableFuture<>();
        final ScheduledFuture<?> checkFuture = executor.scheduleAtFixedRate(() -> {
            if (!this.listening) {
                completionFuture.complete(true);
            }
            invoiceCheckSchedule.fire(new BtcPaySchedule());
        }, 0, 1, TimeUnit.SECONDS);
        completionFuture.whenComplete((result, thrown) -> {
            checkFuture.cancel(true);
        });
    }

    void checkInvoices(@Observes BtcPaySchedule schedule) {
        LOG.info("Checking BtcPay invoices");
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

    private void checkInvoiceStatus(String invoiceId) {
        JsonObject btcPayInvoiceStatus = getInvoice(invoiceId);
        InvoiceUpdated event = new InvoiceUpdated();
        event.id = btcPayInvoiceStatus.getString("invoiceId");
        event.settled = "complete".equals(btcPayInvoiceStatus.getString("status"));
        event.memo = btcPayInvoiceStatus.getString("itemDesc");
        if (event.settled) {
            LOG.info(JsonbBuilder.create().toJson(event));
            LOG.info("Sending update event");
            invoiceUpdateEvent.fireAsync(event);
        }
    }

    public JsonObject getInvoice(String invoiceId) {
        return getInvoiceStatusBuilder(invoiceId).get(JsonObject.class);
    }

    private Invocation.Builder getInvoicesBuilder() {
        return addHeaders(openNodeTarget.path("invoices"));
    }

    private Invocation.Builder getInvoiceStatusBuilder(String id) {
        return openNodeTarget.path("invoice").path("status")
                .queryParam("invoiceId", id)
                .queryParam("paymentMethodId", "BTC_LightningLike")
                .request(MediaType.APPLICATION_JSON_TYPE);
    }


    private Invocation.Builder addHeaders(WebTarget target) {

        return target
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encode(btcPayApiToken.orElseThrow()));
    }

    private String encode(String token) {
        return Base64.getEncoder().encodeToString(token.getBytes());
    }

    private class BtcPaySchedule {
    }
}
