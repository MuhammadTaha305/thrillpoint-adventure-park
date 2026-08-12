/**
 * Base class for every <em>checked</em> business-rule failure in the park system.
 *
 * <p>Checked, because the caller can always do something sensible about a refused
 * booking (show a message, try another ride). Unrecoverable programming errors use
 * {@link ParkRuntimeException} instead.</p>
 *
 * <p>Supports exception translation: the {@code (String, Throwable)} constructor
 * preserves the original failure as {@link #getCause()}.</p>
 */
public class ParkException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * @param message human-readable explanation, safe to show in the GUI
     */
    public ParkException(String message) {
        super(message);
    }

    /**
     * @param message human-readable explanation, safe to show in the GUI
     * @param cause   the lower-level failure being translated
     */
    public ParkException(String message, Throwable cause) {
        super(message, cause);
    }
}
