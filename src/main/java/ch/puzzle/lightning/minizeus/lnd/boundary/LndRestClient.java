package ch.puzzle.lightning.minizeus.lnd.boundary;

import ch.puzzle.lightning.minizeus.SslUtils;
import ch.puzzle.lightning.minizeus.invoices.entity.Invoice;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceCreated;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceUpdated;
import ch.puzzle.lightning.minizeus.lightning.boundary.LightningClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.net.ssl.SSLContext;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Logger;

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
    @ConfigProperty(name = "lnd.config.macaroon-readonly.hex")
    String readonlyMacaroonHex;

    @Inject
    @ConfigProperty(name = "lnd.config.macaroon-invoice.hex")
    String invoiceMacaroonHex;

    @Inject
    @ConfigProperty(name = "lnd.config.cert.path")
    String certPath;


    private WebTarget lndTarget;
    volatile private boolean listening;

    @PostConstruct
    public void init() {
        if (isConfigured()) {
            lndTarget = ClientBuilder
                    .newBuilder()
                    .sslContext(getSslContext())
                    .build()
                    .target(url.orElseThrow());
        }
    }

    private SSLContext getSslContext() {
        return SslUtils.getSslContextForCertificateFile(certPath);
    }


    @Override
    public Invoice generateInvoice(long amount, String memo) {

        return null;
    }

    @Override
    public JsonObject getInfo() {
        return addReadonlyHeaders(lndTarget.path("v1").path("getinfo")).get(JsonObject.class);
    }

    @Override
    public void startInvoiceListen() {

    }

    @Override
    public boolean isConfigured() {
        return url.filter(Predicate.not(String::isEmpty)).isPresent();
    }


    private Invocation.Builder getInvoiceStatusBuilder(String id) {
        return lndTarget.path("invoice").path("status")
                .queryParam("invoiceId", id)
                .queryParam("paymentMethodId", "BTC_LightningLike")
                .request(MediaType.APPLICATION_JSON_TYPE);
    }


    private Invocation.Builder addInvoiceHeaders(WebTarget target) {
        return target
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header("Grpc-Metadata-macaroon", invoiceMacaroonHex);
    }

    private Invocation.Builder addReadonlyHeaders(WebTarget target) {
        return target
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header("Grpc-Metadata-macaroon", readonlyMacaroonHex);
    }

}
