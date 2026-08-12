/**
 * Sky-zone zip line: smallest capacity in the park.
 * Requires at least a {@link Technician#LEVEL_MID} technician to repair.
 */
public class ZipLine extends Ride {
    private static final long serialVersionUID = 2L;

    /**
     * @param name display name
     * @param zone park zone
     */
    public ZipLine(String name, String zone) {
        super(name, zone, 2, 1.3, 4.0);
    }

    @Override
    public String getRideType() {
        return TYPE_ZIP_LINE;
    }
}
