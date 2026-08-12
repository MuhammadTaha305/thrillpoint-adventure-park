/**
 * Role interface for anything that accumulates wear and must be serviced.
 *
 * <p>Kept separate from {@link Bookable} so the maintenance department depends
 * only on the wear/repair capability and the ticketing department depends only
 * on the queueing capability (Interface Segregation).</p>
 */
public interface Maintainable {

    /** Wear percentage at or above which a ride must be taken offline. */
    double WEAR_THRESHOLD = 60.0;

    /** @return current wear as a percentage from 0.0 to 100.0 */
    double getWear();

    /** @return how much wear one completed cycle adds */
    double getWearRate();

    /**
     * @return {@code true} when wear has reached {@link #WEAR_THRESHOLD}
     */
    default boolean needsMaintenance() {
        return getWear() >= WEAR_THRESHOLD;
    }
}
