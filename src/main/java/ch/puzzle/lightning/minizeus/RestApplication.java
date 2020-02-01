package ch.puzzle.lightning.minizeus;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Application;

@ApplicationPath("api")
@Path("")
public class RestApplication extends Application {
    @GET
    public String getTest() {
        return "hello";
    }
}
