/**
 * Valid for exactly one named ride, exactly once.
 *
 * <p>{@link RideCycle} flips {@link #setUsed(boolean)} after the holder completes
 * a cycle.</p>
 */
public class SingleRideTicket extends Ticket {
    private static final long serialVersionUID = 2L;

    private String targetRideName;
    private boolean used;

    /**
     * @param ticketId       unique identifier
     * @param visitorName    the purchaser
     * @param basePrice      price paid at time of sale
     * @param targetRideName the one ride this ticket admits the holder to
     */
    public SingleRideTicket(String ticketId, String visitorName, double basePrice, String targetRideName) {
        super(ticketId, visitorName, basePrice, TYPE_SINGLE);
        setTargetRideName(targetRideName);
        this.used = false;
    }

    /** @return the name of the only ride this ticket is valid for */
    public String getTargetRideName() {
        return targetRideName;
    }

    /**
     * @param targetRideName the only ride this ticket is valid for
     * @throws IllegalArgumentException when blank
     */
    public void setTargetRideName(String targetRideName) {
        if (targetRideName == null || targetRideName.trim().isEmpty()) {
            throw new IllegalArgumentException("Target ride name cannot be empty");
        }
        this.targetRideName = targetRideName;
    }

    /** @return {@code true} once the holder has completed their cycle */
    public synchronized boolean isUsed() {
        return used;
    }

    /** @param used {@code true} to consume the ticket */
    public synchronized void setUsed(boolean used) {
        this.used = used;
    }

    @Override
    public boolean isValidForRide(Ride ride) {
        if (isUsed()) {
            return false;
        }
        return ride.getName().equalsIgnoreCase(targetRideName);
    }
}
