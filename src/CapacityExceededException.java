/**
 * Thrown when a ride's waiting queue cannot accept the requested number of
 * visitors.
 *
 * <p>Carries the numbers as custom fields so the caller can decide whether a
 * smaller group would have fitted.</p>
 */
public class CapacityExceededException extends ParkException {
    private static final long serialVersionUID = 1L;

    private final String rideName;
    private final int requested;
    private final int available;

    /**
     * @param rideName  the ride whose queue is full
     * @param requested how many queue slots the booking needed
     * @param available how many slots were actually free
     */
    public CapacityExceededException(String rideName, int requested, int available) {
        super("The queue for " + rideName + " cannot fit this booking (needed "
              + requested + ", only " + available + " free).");
        this.rideName = rideName;
        this.requested = requested;
        this.available = available;
    }

    /** @return the name of the full ride */
    public String getRideName() {
        return rideName;
    }

    /** @return how many queue slots the booking needed */
    public int getRequested() {
        return requested;
    }

    /** @return how many queue slots were free */
    public int getAvailable() {
        return available;
    }
}
