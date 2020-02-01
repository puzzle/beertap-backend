package ch.puzzle.lightning.minizeus.opennode.boundary;


import ch.puzzle.lightning.minizeus.invoices.entity.Invoice;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceSettled;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.ObservesAsync;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


@Path("opennode")
@ApplicationScoped
public class OpennodeResource {

    @Inject
    OpenNodeClient lightning;


    private Map<String, JsonObject> ordersToCharges = new HashMap<>();

    @GET
    @Produces("application/json")
    public Response getOpennodeInvoice(
            @QueryParam("amount") @DefaultValue("1") long amount,
            @QueryParam("memo") @DefaultValue("default") String memo) {
        System.out.println("getinvoice called");
        String oderId = UUID.randomUUID().toString();
        JsonObject charge = lightning.generateInvoiceWithCallback(oderId, amount, memo, "http://localhost:8080/api/opennode/paid/" + oderId);
        ordersToCharges.put(oderId, charge);
        return Response.temporaryRedirect(URI.create("https://checkout.opennode.com/"+charge.getString("id"))).build();
    }


    @GET
    @Path("paid/{oderId}")
    @Produces("application/json")
    public JsonObject checkPaid(
            @PathParam("oderId") String orderId) {

        JsonObject charge =
                lightning.getInvoice(ordersToCharges.get(orderId).getString("id"));
        return Json.createObjectBuilder().add("status", charge.getString("status")).build();
    }
}
