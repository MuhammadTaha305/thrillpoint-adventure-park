/**
 * Adventure-zone climbing wall: low height limit, low wear.
 * Any technician may repair one.
 */
public class ClimbingWall extends Ride {
    private static final long serialVersionUID = 2L;

    /**
     * @param name display name
     * @param zone park zone
     */
    public ClimbingWall(String name, String zone) {
        super(name, zone, 3, 1.1, 3.0);
    }

    @Override
    public String getRideType() {
        return TYPE_CLIMBING_WALL;
    }
}
