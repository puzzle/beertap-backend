package ch.puzzle.lightning.minizeus.lnd.boundary;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import java.io.IOException;

public class LndAuthHeaderFilter implements ClientRequestFilter {

    public static final String FILTER_HEADER_KEY = "Grpc-Metadata-macaroon";
    private final String macaroonHex;

    public LndAuthHeaderFilter(String macaroonHex) {
        this.macaroonHex = macaroonHex;
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        requestContext.getHeaders().add(FILTER_HEADER_KEY, macaroonHex);
    }
}