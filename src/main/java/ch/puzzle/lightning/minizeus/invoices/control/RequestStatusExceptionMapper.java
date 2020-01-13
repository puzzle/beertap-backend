package ch.puzzle.lightning.minizeus.invoices.control;

import ch.puzzle.lightning.minizeus.invoices.entity.RequestStatusException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class RequestStatusExceptionMapper implements ExceptionMapper<RequestStatusException> {


    @Override
    public Response toResponse(RequestStatusException e) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
    }
}