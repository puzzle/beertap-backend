package ch.puzzle.lightning.minizeus;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import java.util.Optional;
import java.util.function.Predicate;

@Provider
public class CorsFilter implements ContainerResponseFilter {

    @Inject
    @ConfigProperty(name = "app.allowOrigin")
    Optional<String> allowOrigin;

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        String origin = allowOrigin
                .filter(Predicate.not(String::isEmpty))
                .or(() -> Optional.ofNullable(requestContext.getHeaders().getFirst("Origin")))
                .orElse("*");
        System.out.println(origin);
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();
        headers.add("Access-Control-Allow-Origin", origin);
        headers.add("Access-Control-Allow-Credentials", "true");
        headers.add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization");
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
    }
}