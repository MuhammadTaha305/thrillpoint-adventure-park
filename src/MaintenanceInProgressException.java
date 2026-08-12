/**
 * Thrown when a booking targets a ride that is currently
 * {@link Ride#STATUS_UNDER_MAINTENANCE}.
 *
 * <p>Distinct from {@link RideClosedException} because this state is temporary —
 * the GUI can reasonably suggest "try again shortly".</p>
 */
public class MaintenanceInProgressException extends ParkException {
    private static final long serialVersionUID = 1L;

    private final String rideName;

    /**
     * @param rideName the ride that is under maintenance
     */
    public MaintenanceInProgressException(String rideName) {
        super(rideName + " is currently under maintenance. Please try again shortly.");
        this.rideName = rideName;
    }

    /** @return the name of the ride under maintenance */
    public String getRideName() {
        return rideName;
    }
}
