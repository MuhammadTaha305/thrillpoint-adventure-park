/**
 * Thrown when a visitor is shorter than the ride's safety minimum.
 *
 * <p>Carries the two heights as custom fields so the GUI can render a precise
 * message without re-parsing {@link #getMessage()}.</p>
 */
public class HeightRestrictionException extends ParkException {
    private static final long serialVersionUID = 1L;

    private final double actualHeight;
    private final double requiredHeight;
    private final String rideName;

    /**
     * @param rideName       the ride that refused the visitor
     * @param actualHeight   the visitor's height in metres
     * @param requiredHeight the ride's minimum height in metres
     */
    public HeightRestrictionException(String rideName, double actualHeight, double requiredHeight) {
        super("Visitor is too short for " + rideName + " ("
              + String.format("%.2fm", actualHeight) + " < "
              + String.format("%.2fm", requiredHeight) + ").");
        this.rideName = rideName;
        this.actualHeight = actualHeight;
        this.requiredHeight = requiredHeight;
    }

    /** @return the visitor's height in metres */
    public double getActualHeight() {
        return actualHeight;
    }

    /** @return the ride's minimum height in metres */
    public double getRequiredHeight() {
        return requiredHeight;
    }

    /** @return the name of the ride that refused the visitor */
    public String getRideName() {
        return rideName;
    }
}
