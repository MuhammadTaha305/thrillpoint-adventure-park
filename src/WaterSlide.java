/**
 * Aqua-zone slide: moderate capacity and wear.
 * Requires at least a {@link Technician#LEVEL_MID} technician to repair.
 */
public class WaterSlide extends Ride {
    private static final long serialVersionUID = 2L;

    /**
     * @param name display name
     * @param zone park zone
     */
    public WaterSlide(String name, String zone) {
        super(name, zone, 4, 1.2, 5.0);
    }

    @Override
    public String getRideType() {
        return TYPE_WATER_SLIDE;
    }
}
