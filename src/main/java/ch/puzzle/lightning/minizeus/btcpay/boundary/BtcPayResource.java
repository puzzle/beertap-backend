package ch.puzzle.lightning.minizeus.btcpay.boundary;


import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Path("btcpay")
@ApplicationScoped
public class BtcPayResource {

    @Inject
    BtcPayClient lightning;


    private Map<String, JsonObject> ordersToCharges = new HashMap<>();

    @GET
    @Produces("application/json")
    public Response getBtcPayInvoice(
            @QueryParam("amount") @DefaultValue("1") long amount,
            @QueryParam("memo") @DefaultValue("default") String memo) {
        System.out.println("getinvoice called");
        String oderId = UUID.randomUUID().toString();
        JsonObject invoice = lightning.generateInvoiceWithRedirect(oderId, amount, memo, "http://localhost:8080/api/opennode/paid/" + oderId);
        System.out.println(invoice.toString());
        ordersToCharges.put(oderId, invoice);
        return Response.temporaryRedirect(URI.create("https://btcpay.lightning-test.puzzle.ch/invoice?id="+invoice.getString("id"))).build();
    }


    @GET
    @Path("paid/{oderId}")
    @Produces("application/json")
    public JsonObject checkBtcPayPaid(
            @PathParam("oderId") String orderId) {

        JsonObject charge =
                lightning.getInvoice(ordersToCharges.get(orderId).getString("id"));
        return Json.createObjectBuilder().add("status", charge.getString("status")).build();
    }
}
