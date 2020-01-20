package ch.puzzle.lightning.minizeus.invoices.entity;

import org.lightningj.lnd.wrapper.message.AddInvoiceResponse;

import static ch.puzzle.lightning.minizeus.conversions.boundary.ConvertService.bytesToHex;

public class Invoice {
    public String rHash;
    public String memo;
    public String paymentRequest;
    public long expiry;
    public boolean settled;

    public static Invoice fromLndInvoice(org.lightningj.lnd.wrapper.message.Invoice lndInvoice) {
        Invoice invoice = new Invoice();
        invoice.rHash = bytesToHex(lndInvoice.getRHash());
        invoice.memo = lndInvoice.getMemo();
        invoice.expiry = lndInvoice.getExpiry();
        invoice.paymentRequest = lndInvoice.getPaymentRequest();
        invoice.settled = lndInvoice.getSettled();
        return invoice;
    }

    public static Invoice fromAddInvoice(AddInvoiceResponse response) {
        Invoice invoice = new Invoice();
        invoice.settled = false;
        invoice.paymentRequest = response.getPaymentRequest();
        invoice.rHash = bytesToHex(response.getRHash());
        return invoice;
    }
}
