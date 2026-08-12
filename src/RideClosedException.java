/**
 * Thrown when a booking targets a ride whose status is
 * {@link Ride#STATUS_CLOSED}.
 */
public class RideClosedException extends ParkException {
    private static final long serialVersionUID = 1L;

    private final String rideName;

    /**
     * @param rideName the ride that is closed
     */
    public RideClosedException(String rideName) {
        super(rideName + " is closed and is not accepting bookings.");
        this.rideName = rideName;
    }

    /** @return the name of the closed ride */
    public String getRideName() {
        return rideName;
    }
}
