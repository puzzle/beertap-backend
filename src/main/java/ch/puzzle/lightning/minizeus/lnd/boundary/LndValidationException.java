package ch.puzzle.lightning.minizeus.lnd.boundary;

public class LndValidationException extends RuntimeException {
    public LndValidationException(Exception e) {
        super(e);
    }
}
