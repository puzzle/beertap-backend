package ch.puzzle.lightning.minizeus.opennode.boundary;

import ch.puzzle.lightning.minizeus.invoices.boundary.InvoiceCache;
import ch.puzzle.lightning.minizeus.invoices.boundary.LightningClient;
import ch.puzzle.lightning.minizeus.invoices.entity.Invoice;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceCreated;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceUpdated;
import ch.puzzle.lightning.minizeus.opennode.entity.OpenNodeCharge;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
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
    InvoiceCache invoiceCache;

    private WebTarget openNodeTarget;

    public Invoice generateInvoice(long amount, String memo) {
        OpenNodeCharge charge = new OpenNodeCharge();
        charge.amount = amount;
        charge.description = memo;
        JsonObject response = createInvoice(charge).getJsonObject("data");
        Invoice invoice = Invoice.fromOpenNodeCharge(response);
        invoiceCreatedEvent.fire(new InvoiceCreated(invoice));
        return invoice;
    }


    public JsonObject getInfo() {
        return getChargesBuilder().get(JsonObject.class);
    }

    private JsonObject createInvoice(OpenNodeCharge charge) {
        try {
            return getChargesBuilder().post(Entity.json(charge), JsonObject.class);
        } catch (HttpResponseException e) {
            e.getResponse().getHeaders()
                    .forEach(h -> LOG.info(h.toString()));
            LOG.info(e.getResponse().getStatus() + " STATUS ");
            return JsonObject.EMPTY_JSON_OBJECT;
        }
    }

    public boolean isConfigured() {
        return openNodeApiToken.isPresent() && openNodeApiUri.isPresent();
    }


    public void init(@Observes @Initialized(ApplicationScoped.class) Object init) {
        openNodeTarget = ClientBuilder
                .newClient()
                .target(openNodeApiUri.orElseThrow())
                .register(new LoggingFilter())
                .path("v1");
        LOG.info("INIT OPENNODE");
        subscribeToInvoices();
    }

    private void subscribeToInvoices() {
        ForkJoinPool.commonPool().execute(this::checkInvoices);
    }

    private void checkInvoices() {
        LOG.info("checking for new invoice status");
        for (String invoiceId : invoiceCache.getPendingInvoices()) {
            getInvoiceStatus(invoiceId);
        }
        try {
            TimeUnit.SECONDS.sleep(1L);
            checkInvoices();
        } catch (InterruptedException e) {
            LOG.severe(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void getInvoiceStatus(String invoiceId) {
        JsonObject charge = getChargeBuilder(invoiceId).get(JsonObject.class).getJsonObject("data");
        if ("paid".equals(charge.getString("status"))) {
            invoiceUpdateEvent.fire(new InvoiceUpdated(
                    Invoice.fromOpenNodeCharge(charge)));
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
}
