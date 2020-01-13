package ch.puzzle.lightning.minizeus.invoices.control;

import ch.puzzle.lightning.minizeus.invoices.entity.ZeusInternalException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class ZeusInternalExceptionMapper implements ExceptionMapper<ZeusInternalException> {


    @Override
    public Response toResponse(ZeusInternalException e) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
}