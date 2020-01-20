package ch.puzzle.lightning.minizeus.invoices.entity;

public class InvoiceCreated {

    public final long invoiceExpiry;
    public final String rHash;

    public InvoiceCreated(String rHash, long invoiceExpiry) {
        this.rHash = rHash;
        this.invoiceExpiry =  invoiceExpiry;
    }
}
