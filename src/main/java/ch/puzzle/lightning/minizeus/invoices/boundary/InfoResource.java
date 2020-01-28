package ch.puzzle.lightning.minizeus.invoices.boundary;


import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

@Path("info")
@ApplicationScoped
public class InfoResource {

    @Inject
    Lightning lightning;


    @GET
    @Produces("application/json")
    public JsonObject doGet() {
        return lightning.getInfo();
    }
}

