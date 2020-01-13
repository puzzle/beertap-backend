package ch.puzzle.lightning.minizeus.invoices.control;

import ch.puzzle.lightning.minizeus.invoices.entity.LndValidationException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class LndValidationExceptionMapper implements ExceptionMapper<LndValidationException> {


    @Override
    public Response toResponse(LndValidationException e) {
        return Response.status(Response.Status.BAD_REQUEST).build();
    }
}