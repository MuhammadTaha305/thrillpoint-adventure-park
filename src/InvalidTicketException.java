/**
 * Thrown when the requested ticket cannot be issued or does not grant access to
 * the requested ride.
 */
public class InvalidTicketException extends ParkException {
    private static final long serialVersionUID = 1L;

    /**
     * @param message explanation of why the ticket is invalid
     */
    public InvalidTicketException(String message) {
        super(message);
    }
}
