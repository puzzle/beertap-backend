package ch.puzzle.lightning.minizeus.invoices.entity;

import static ch.puzzle.lightning.minizeus.conversions.boundary.ConvertService.bytesToHex;

public class Invoice {
    public String rHash;
    public String memo;
    public String paymentRequest;
    public boolean settled;

    public static Invoice fromLndInvoice(org.lightningj.lnd.wrapper.message.Invoice lndInvoice) {
        Invoice invoice = new Invoice();
        invoice.rHash = bytesToHex(lndInvoice.getRHash());
        invoice.memo = lndInvoice.getMemo();
        invoice.paymentRequest = lndInvoice.getPaymentRequest();
        invoice.settled = lndInvoice.getSettled();
        return invoice;
    }
}
