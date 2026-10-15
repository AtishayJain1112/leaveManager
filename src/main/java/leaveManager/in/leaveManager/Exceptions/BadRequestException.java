package leaveManager.in.leaveManager.Exceptions;

/** A business-rule validation failure (bad dates, not enough leave balance...). Maps to HTTP 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
