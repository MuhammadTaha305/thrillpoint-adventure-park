import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A themed area of the park that groups rides together.
 *
 * <p>Aggregation, not composition: a {@code Zone} holds rides but does not own
 * their lifetime — a ride can be moved between zones and still be the same
 * object, and destroying a zone would not destroy its rides. Contrast with
 * {@link Ride} and its visitor queue, which is true composition.</p>
 *
 * <p>Zones are the unit the management reports aggregate over.</p>
 */
public class Zone implements Serializable, Identifiable {
    private static final long serialVersionUID = 1L;

    private String name;
    private List<Ride> rides;

    /**
     * @param name the zone's display name, e.g. {@code "Splash Zone"}
     * @throws IllegalArgumentException when blank
     */
    public Zone(String name) {
        setName(name);
        this.rides = new ArrayList<>();
    }

    /** @return the zone's display name */
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     *
     * <p>A zone is identified by its name.</p>
     */
    @Override
    public String getId() {
        return name;
    }

    /**
     * @param name the zone's display name
     * @throws IllegalArgumentException when blank
     */
    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Zone name cannot be empty");
        }
        this.name = name;
    }

    /**
     * @param ride the ride to place in this zone; {@code null} and duplicates are
     *             ignored
     */
    public synchronized void addRide(Ride ride) {
        if (ride != null && !rides.contains(ride)) {
            rides.add(ride);
        }
    }

    /** @return an unmodifiable view of the rides in this zone */
    public synchronized List<Ride> getRides() {
        return Collections.unmodifiableList(new ArrayList<>(rides));
    }

    /** @return how many rides sit in this zone */
    public synchronized int getRideCount() {
        return rides.size();
    }

    /** @return combined revenue of every ride in this zone */
    public synchronized double getTotalRevenue() {
        double total = 0.0;
        for (Ride r : rides) {
            total += r.getRevenue();
        }
        return total;
    }

    /** @return combined riders carried by every ride in this zone */
    public synchronized int getTotalRidersServed() {
        int total = 0;
        for (Ride r : rides) {
            total += r.getRidersServed();
        }
        return total;
    }

    /** @return combined queue length across every ride in this zone */
    public synchronized int getTotalQueueSize() {
        int total = 0;
        for (Ride r : rides) {
            total += r.getQueueSize();
        }
        return total;
    }

    /** @return how many rides in this zone are currently accepting visitors */
    public synchronized int getOperationalRideCount() {
        int count = 0;
        for (Ride r : rides) {
            if (r.isBookable()) {
                count++;
            }
        }
        return count;
    }

    /** @return the mean uptime percentage across this zone's rides */
    public synchronized double getAverageUptimePercent() {
        if (rides.isEmpty()) {
            return 100.0;
        }
        double total = 0.0;
        for (Ride r : rides) {
            total += r.getUptimePercent();
        }
        return total / rides.size();
    }

    @Override
    public String toString() {
        return name + " (" + getRideCount() + " rides)";
    }
}
