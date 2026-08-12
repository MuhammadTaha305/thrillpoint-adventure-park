/**
 * Base class for every <em>unchecked</em> park failure — programming errors and
 * broken invariants that a caller cannot sensibly recover from.
 *
 * <p>Contrast with {@link ParkException}, which represents business rules the
 * caller is expected to handle.</p>
 */
public class ParkRuntimeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * @param message explanation of the broken invariant
     */
    public ParkRuntimeException(String message) {
        super(message);
    }

    /**
     * @param message explanation of the broken invariant
     * @param cause   the lower-level failure being translated
     */
    public ParkRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
