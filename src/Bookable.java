/**
 * Role interface for anything a visitor can queue for.
 *
 * <p>Exists so {@code TicketingService} depends on the <em>capability</em> of
 * being bookable rather than on the concrete {@link Ride} class (Dependency
 * Inversion).</p>
 */
public interface Bookable {

    /** Maximum number of visitors allowed to wait at once. */
    int MAX_QUEUE_LENGTH = 30;

    /** @return the display name of this bookable attraction */
    String getName();

    /** @return the minimum visitor height in metres */
    double getMinHeight();

    /** @return how many visitors are currently waiting */
    int getQueueSize();

    /**
     * @return how many more visitors the queue can accept right now
     */
    default int getFreeQueueSlots() {
        int free = MAX_QUEUE_LENGTH - getQueueSize();
        return free < 0 ? 0 : free;
    }

    /**
     * @param height a visitor's height in metres
     * @return {@code true} when the visitor is tall enough
     */
    default boolean admitsHeight(double height) {
        return height >= getMinHeight();
    }
}
