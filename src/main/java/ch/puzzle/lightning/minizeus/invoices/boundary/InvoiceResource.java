package ch.puzzle.lightning.minizeus.invoices.boundary;


import ch.puzzle.lightning.minizeus.invoices.entity.Invoice;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceSettled;
import ch.puzzle.lightning.minizeus.lightning.boundary.Lightning;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.ObservesAsync;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;


@Path("invoice")
@ApplicationScoped
public class InvoiceResource {

    @Inject
    Instance<Lightning> lightning;

    private volatile SseBroadcaster sseBroadcaster;
    private OutboundSseEvent.Builder eventBuilder;

    @Context
    Sse sse;

    @PostConstruct
    public void initSse() {
        this.sseBroadcaster = sse.newBroadcaster();
        this.eventBuilder = sse.newEventBuilder();
        sseBroadcaster.onClose(sseEventSink -> System.out.println("subscription closed"));
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Invoice getInvoice(
            @QueryParam("amount") @DefaultValue("1") long amount,
            @QueryParam("memo") @DefaultValue("default") String memo) {
        return lightning.get().generateInvoice(amount, memo);
    }

    @GET
    @Path("subscribe")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Response subscribe(@Context SseEventSink sseEventSink) {
        System.out.println("new subscription");
        this.sseBroadcaster.register(sseEventSink);
        return Response.ok()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Credentials", "true")
                .header("Access-Control-Allow-Headers",
                        "origin, content-type, accept, authorization")
                .header("Access-Control-Allow-Methods",
                        "GET, POST, PUT, DELETE, OPTIONS, HEAD")
                .build();
    }

    public void sendMessage(@ObservesAsync InvoiceSettled value) {
        System.out.println("Sending paid invoice");
        OutboundSseEvent sseEvent = eventBuilder.name("invoice")
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .data(InvoiceSettled.class, value)
                .build();
        this.sseBroadcaster.broadcast(sseEvent);
    }
}
