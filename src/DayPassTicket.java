/**
 * Unlimited rides for the rest of the operating day.
 *
 * <p>Expires when {@link ParkClock} closes the park — see FR-11. The expiry check
 * itself lives in {@link TicketingService#verifyBoarding(Visitor, Ride)}, so this
 * class only has to say "any ride".</p>
 */
public class DayPassTicket extends Ticket {
    private static final long serialVersionUID = 2L;

    /**
     * @param ticketId    unique identifier
     * @param visitorName the purchaser
     * @param basePrice   price paid at time of sale
     */
    public DayPassTicket(String ticketId, String visitorName, double basePrice) {
        super(ticketId, visitorName, basePrice, TYPE_DAY_PASS);
    }

    @Override
    public boolean isValidForRide(Ride ride) {
        return true;
    }
}
