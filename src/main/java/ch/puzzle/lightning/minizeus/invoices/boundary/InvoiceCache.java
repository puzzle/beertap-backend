package ch.puzzle.lightning.minizeus.invoices.boundary;

import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceCreated;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceSettled;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceUpdated;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.ObservesAsync;
import javax.inject.Inject;
import javax.json.bind.JsonbBuilder;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

@ApplicationScoped
public class InvoiceCache {

    private static final Logger LOG = Logger.getLogger(InvoiceCache.class.getName());

    @Inject
    Event<InvoiceSettled> invoiceSettledEvent;

    private final ConcurrentMap<String, InvoiceCreated> invoices = new ConcurrentHashMap<>();

    public void invoiceCreated(@ObservesAsync InvoiceCreated value) {
        System.out.println(JsonbBuilder.create().toJson(value));
        invoices.put(value.id, value);
        if (invoices.size() > 1000) {
            LOG.severe("Cache is getting big!");
        }
    }

    public void invoiceUpdated(@ObservesAsync InvoiceUpdated value) {
        System.out.println("Received update event");
        long now = now();
        cleanCache(now);
        if (Optional.ofNullable(invoices.get(value.id))
                .filter(i -> i.expiry  > now)
                .isPresent() &&
                value.settled) {
            System.out.println("sending settled event");
            invoiceSettledEvent.fireAsync(getInvoiceSettled(value))
                    .whenComplete((invoiceSettled, throwable) -> invoices.remove(invoiceSettled.id));
        }
    }

    private InvoiceSettled getInvoiceSettled(InvoiceUpdated value) {
        InvoiceSettled invoiceSettled = new InvoiceSettled();
        invoiceSettled.id = value.id;
        invoiceSettled.settled = value.settled;
        invoiceSettled.memo = value.memo;
        return invoiceSettled;
    }

    private long now() {
        return Instant.now().getEpochSecond();
    }

    private void cleanCache(long now) {
        long sizeBefore = invoices.size();
        invoices.entrySet().removeIf(entry -> entry.getValue().expiry < now);
        LOG.info("Removed " + (sizeBefore - invoices.size()) + " invoices, actual size: " + invoices.size());
    }

    public Set<String> getPendingInvoices() {
        return Set.copyOf(invoices.keySet());
    }
}
