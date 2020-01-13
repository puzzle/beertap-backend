package ch.puzzle.lightning.minizeus.invoices.boundary;


import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Path("info")
@ApplicationScoped
public class InfoResource {

    @Inject
    LndService lndService;


    @GET
    @Produces("application/json")
    public JsonObject doGet() {
        return lndService.getInfo().toJson().build();
    }
}

