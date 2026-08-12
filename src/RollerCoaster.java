/**
 * High-thrill coaster: small capacity, tall height limit, heavy wear.
 * Only a {@link Technician#LEVEL_SENIOR} technician may repair one.
 */
public class RollerCoaster extends Ride {
    private static final long serialVersionUID = 2L;

    /**
     * @param name display name
     * @param zone park zone
     */
    public RollerCoaster(String name, String zone) {
        super(name, zone, 6, 1.4, 10.0);
    }

    @Override
    public String getRideType() {
        return TYPE_ROLLER_COASTER;
    }
}
