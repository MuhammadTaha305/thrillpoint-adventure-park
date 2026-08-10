/**
 * Family-zone Ferris wheel: largest capacity, gentlest wear.
 * Any technician may repair one.
 */
public class FerrisWheel extends Ride {
    private static final long serialVersionUID = 2L;

    /**
     * @param name display name
     * @param zone park zone
     */
    public FerrisWheel(String name, String zone) {
        super(name, zone, 8, 1.0, 1.5);
    }

    @Override
    public String getRideType() {
        return TYPE_FERRIS_WHEEL;
    }
}
