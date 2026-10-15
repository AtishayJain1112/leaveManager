package leaveManager.in.leaveManager.Exceptions;

/** The request is valid but clashes with current state (duplicate email, overlapping dates, wrong status). Maps to HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
