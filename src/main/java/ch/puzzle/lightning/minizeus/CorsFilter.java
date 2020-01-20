package ch.puzzle.lightning.minizeus;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.util.Optional;

@Provider
public class CorsFilter implements ContainerResponseFilter {

    @ConfigProperty(name = "app.allow-origin", defaultValue = "")
    String allowOrigin;

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        String origin = Optional.ofNullable(allowOrigin)
                .orElseGet(() -> requestContext.getHeaders().getFirst("Origin"));
        responseContext.getHeaders().add(
                "Access-Control-Allow-Origin", origin);
        responseContext.getHeaders().add(
                "Access-Control-Allow-Credentials", "true");
        responseContext.getHeaders().add(
                "Access-Control-Allow-Headers",
                "origin, content-type, accept, authorization");
        responseContext.getHeaders().add(
                "Access-Control-Allow-Methods",
                "GET, POST, PUT, DELETE, OPTIONS, HEAD");
    }
}