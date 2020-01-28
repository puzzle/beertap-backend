package ch.puzzle.lightning.minizeus.opennode.boundary;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;
import java.util.logging.Logger;

public class LoggingFilter implements ContainerResponseFilter {

    Logger LOG = Logger.getLogger(LoggingFilter.class.getSimpleName());

    @Override
    public void filter(ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext) throws IOException {
        LOG.info(containerResponseContext.getStatus() + " RESPONSE STATUS");
    }
}