package ch.puzzle.lightning.minizeus.invoices.boundary;


import ch.puzzle.lightning.minizeus.invoices.entity.Invoice;
import ch.puzzle.lightning.minizeus.invoices.entity.InvoiceSettled;
import ch.puzzle.lightning.minizeus.lightning.boundary.Lightning;
import org.jboss.resteasy.annotations.SseElementType;

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
    public void subscribe(@Context SseEventSink sseEventSink, @Context Sse sse) {
        System.out.println("new subscription");
        registerSink(sseEventSink, sse);
    }

    private void registerSink(SseEventSink sseEventSink, Sse sse) {
        if (this.sseBroadcaster == null) {
            initSse(sse);
        }
        this.sseBroadcaster.register(sseEventSink);
    }

    public void initSse(Sse sse) {
        this.sseBroadcaster = sse.newBroadcaster();
        this.eventBuilder = sse.newEventBuilder();
        sseBroadcaster.onClose(sseEventSink -> System.out.println("subscription closed"));
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
