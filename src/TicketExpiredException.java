/**
 * Thrown when a ticket is presented after the park has closed for the day.
 *
 * @see ParkClock#closeParkForTheDay()
 */
public class TicketExpiredException extends ParkException {
    private static final long serialVersionUID = 1L;

    private final String ticketId;

    /**
     * @param ticketId the identifier of the expired ticket
     */
    public TicketExpiredException(String ticketId) {
        super("Ticket " + ticketId + " has expired (the park has closed for the day).");
        this.ticketId = ticketId;
    }

    /** @return the identifier of the expired ticket */
    public String getTicketId() {
        return ticketId;
    }
}
