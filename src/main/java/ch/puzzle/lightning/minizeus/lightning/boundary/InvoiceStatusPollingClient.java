package ch.puzzle.lightning.minizeus.lightning.boundary;

import ch.puzzle.lightning.minizeus.invoices.boundary.InvoiceCache;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import java.util.concurrent.*;
import java.util.function.Consumer;

@ApplicationScoped
public class InvoiceStatusPollingClient {

    private boolean listening;
    private ScheduledExecutorService executor;
    @Inject
    private InvoiceCache invoiceCache;


    @PostConstruct
    public void init() {
        this.executor = Executors.newSingleThreadScheduledExecutor();
    }

    public CompletableFuture<Boolean> pollForCompletion(Consumer<String> invoiceIdChecker) {
        CompletableFuture<Boolean> completionFuture = new CompletableFuture<>();
        final ScheduledFuture<?> checkFuture = executor.scheduleAtFixedRate(() -> {
            if (!this.listening) {
                completionFuture.complete(true);
            }
            checkInvoices(invoiceIdChecker);
        }, 0, 1, TimeUnit.SECONDS);
        completionFuture.whenComplete((result, thrown) -> {
            checkFuture.cancel(true);
        });
        return completionFuture;
    }

    private void checkInvoices(Consumer<String> invoiceIdChecker) {
        System.out.println("Checking invoices");
        for (String invoiceId : this.invoiceCache.getPendingInvoices()) {
            invoiceIdChecker.accept(invoiceId);
        }
        try {
            TimeUnit.SECONDS.sleep(1L);
        } catch (InterruptedException e) {
            System.err.println(e.getMessage());
            throw new RuntimeException(e);
        }
    }


    public void destroy(@Observes @Destroyed(ApplicationScoped.class) Object init) {
        this.listening = false;
    }
}
