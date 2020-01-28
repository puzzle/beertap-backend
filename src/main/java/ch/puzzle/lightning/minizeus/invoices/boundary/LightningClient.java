package ch.puzzle.lightning.minizeus.invoices.boundary;

import ch.puzzle.lightning.minizeus.invoices.entity.Invoice;

import javax.json.JsonObject;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

public interface LightningClient {

    Invoice generateInvoice(long amount, String memo);

    JsonObject getInfo();


    default long getInvoiceExpiry() {
        return Duration.of(10, ChronoUnit.MINUTES).get(ChronoUnit.SECONDS);
    }

    boolean isConfigured();
}
