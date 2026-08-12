/**
 * Unlimited rides plus queue priority.
 *
 * <p>The queue-jumping itself is implemented in
 * {@link Ride#enqueueVisitor(Visitor)}, which inserts VIP holders ahead of
 * standard visitors but behind earlier VIPs.</p>
 */
public class VIPFastPassTicket extends Ticket {
    private static final long serialVersionUID = 2L;

    /**
     * @param ticketId    unique identifier
     * @param visitorName the purchaser
     * @param basePrice   price paid at time of sale
     */
    public VIPFastPassTicket(String ticketId, String visitorName, double basePrice) {
        super(ticketId, visitorName, basePrice, TYPE_VIP);
    }

    @Override
    public boolean isValidForRide(Ride ride) {
        return true;
    }
}
