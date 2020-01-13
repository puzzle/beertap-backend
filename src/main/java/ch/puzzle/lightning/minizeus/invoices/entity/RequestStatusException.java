package ch.puzzle.lightning.minizeus.invoices.entity;

public class RequestStatusException extends RuntimeException {
    public RequestStatusException(Exception e) {
        super(e);
    }
}
