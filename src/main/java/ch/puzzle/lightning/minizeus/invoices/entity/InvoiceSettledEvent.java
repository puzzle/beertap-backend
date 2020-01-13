package ch.puzzle.lightning.minizeus.invoices.entity;

public class InvoiceSettledEvent {
    public Invoice invoice;

    public InvoiceSettledEvent(Invoice invoice) {
        this.invoice = invoice;
    }
}
