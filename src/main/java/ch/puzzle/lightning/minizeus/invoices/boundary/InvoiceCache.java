package ch.puzzle.lightning.minizeus.invoices.boundary;

import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceCreated;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceSettled;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceUpdated;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.ObservesAsync;
import javax.inject.Inject;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

@ApplicationScoped
public class InvoiceCache {

    private static final Logger LOG = Logger.getLogger(InvoiceCache.class.getName());

    @Inject
    Event<InvoiceSettled> invoiceSettledEvent;

    private final ConcurrentMap<String, Long> invoices = new ConcurrentHashMap<>();

    public void invoiceCreated(@ObservesAsync InvoiceCreated value) {
        invoices.put(value.rHash, now() + value.invoiceExpiry);
    }

    public void invoiceUpdated(@ObservesAsync InvoiceUpdated value) {
        long now = now();
        cleanCache(now);
        if (invoices.getOrDefault(value.invoice.rHash, 0L) > now &&
                value.invoice.settled) {
            invoiceSettledEvent.fireAsync(new InvoiceSettled(value.invoice));
            invoices.remove(value.invoice.rHash);
        }
    }

    private long now() {
        return Instant.now().getEpochSecond();
    }

    private void cleanCache(long now) {
        long sizeBefore = invoices.size();
        invoices.entrySet().removeIf(entry -> entry.getValue() < now);
        LOG.info("Removed " + (sizeBefore - invoices.size()) + " invoices");
    }
}
