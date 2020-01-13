package ch.puzzle.lightning.minizeus.invoices.entity;

public class LndValidationException extends RuntimeException {
    public LndValidationException(Exception e) {
        super(e);
    }
}
