import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base of every attraction in the park, and the system's main piece of
 * shared mutable state.
 *
 * <p><strong>Locking contract.</strong> Every mutator and every getter that reads
 * mutable state is {@code synchronized} on the ride instance. The park-wide lock
 * ordering is <em>always</em> {@code Ride} first, then {@link ParkState} — see
 * §8.4 of the design document. Never call a {@code synchronized} {@code ParkState}
 * method while holding a ride lock in the opposite order, or the two departments
 * will deadlock.</p>
 *
 * <p><strong>Cycle protocol.</strong> A ride cycle must not hold the ride lock
 * while it sleeps. Callers use {@link #beginCycle()}, sleep <em>outside</em> the
 * lock, then call {@link #completeCycle(int)} and one of
 * {@link #reopenIfRunning()} / {@link #takeOffline()}.</p>
 */
public abstract class Ride implements Serializable, Bookable, Maintainable {
    private static final long serialVersionUID = 2L;

    /** Accepting bookings and ready to run. */
    public static final int STATUS_OPEN = 0;

    /** Mid-cycle; riders are on board. */
    public static final int STATUS_RUNNING = 1;

    /** Offline for repair; refuses all bookings. */
    public static final int STATUS_UNDER_MAINTENANCE = 2;

    /** Offline by management decision; refuses all bookings. */
    public static final int STATUS_CLOSED = 3;

    /** Ride type token stored in {@code rides.csv}. */
    public static final String TYPE_ROLLER_COASTER = "RollerCoaster";

    /** Ride type token stored in {@code rides.csv}. */
    public static final String TYPE_WATER_SLIDE = "WaterSlide";

    /** Ride type token stored in {@code rides.csv}. */
    public static final String TYPE_ZIP_LINE = "ZipLine";

    /** Ride type token stored in {@code rides.csv}. */
    public static final String TYPE_CLIMBING_WALL = "ClimbingWall";

    /** Ride type token stored in {@code rides.csv}. */
    public static final String TYPE_FERRIS_WHEEL = "FerrisWheel";

    private String name;
    private String zone;
    private int capacity;
    private double minHeight;
    private double wear;
    private double wearRate;
    private int status;
    private int ridersServed;
    private double revenue;
    private int cyclesRun;

    /** Composition: the ride owns its waiting queue for its whole lifetime. */
    private List<Visitor> queue;

    // Availability accounting. Transient because it measures the current run:
    // carrying a downtime total across a restart, while the uptime window resets,
    // would produce a meaningless ratio. Main calls beginUptimeWindow() on every
    // ride at startup.
    private transient long uptimeWindowStart;
    private transient long totalOfflineMs;
    private transient long offlineSince;

    /**
     * @param name      display name, unique within the park
     * @param zone      the park zone this ride sits in
     * @param capacity  riders carried per cycle
     * @param minHeight safety minimum height in metres
     * @param wearRate  wear percentage added by one completed cycle
     */
    public Ride(String name, String zone, int capacity, double minHeight, double wearRate) {
        setName(name);
        setZone(zone);
        setCapacity(capacity);
        setMinHeight(minHeight);
        setWearRate(wearRate);
        this.wear = 0.0;
        this.status = STATUS_OPEN;
        this.ridersServed = 0;
        this.revenue = 0.0;
        this.cyclesRun = 0;
        this.queue = new ArrayList<>();
        beginUptimeWindow();
    }

    /**
     * Identifies the concrete subclass for persistence.
     *
     * <p>Stored as an explicit {@code RideType} column in {@code rides.csv} so
     * that renaming a ride can never change what class it loads back as.</p>
     *
     * @return one of the {@code TYPE_*} string constants
     */
    public abstract String getRideType();

    /**
     * Static factory that rebuilds the right subclass from a persisted type
     * token.
     *
     * @param rideType one of the {@code TYPE_*} string constants
     * @param name     display name
     * @param zone     park zone
     * @return a new ride of the requested type
     * @throws EntityNotFoundException when {@code rideType} is not recognised
     */
    public static Ride createByType(String rideType, String name, String zone) {
        if (TYPE_ROLLER_COASTER.equalsIgnoreCase(rideType)) {
            return new RollerCoaster(name, zone);
        } else if (TYPE_WATER_SLIDE.equalsIgnoreCase(rideType)) {
            return new WaterSlide(name, zone);
        } else if (TYPE_ZIP_LINE.equalsIgnoreCase(rideType)) {
            return new ZipLine(name, zone);
        } else if (TYPE_CLIMBING_WALL.equalsIgnoreCase(rideType)) {
            return new ClimbingWall(name, zone);
        } else if (TYPE_FERRIS_WHEEL.equalsIgnoreCase(rideType)) {
            return new FerrisWheel(name, zone);
        }
        throw new EntityNotFoundException("Ride type", String.valueOf(rideType));
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return name;
    }

    /**
     * @param name the display name
     * @throws IllegalArgumentException when blank
     */
    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Ride name cannot be empty");
        }
        this.name = name;
    }

    /** @return the park zone this ride sits in */
    public String getZone() {
        return zone;
    }

    /**
     * @param zone the park zone
     * @throws IllegalArgumentException when blank
     */
    public void setZone(String zone) {
        if (zone == null || zone.trim().isEmpty()) {
            throw new IllegalArgumentException("Zone name cannot be empty");
        }
        this.zone = zone;
    }

    /** @return riders carried per cycle */
    public int getCapacity() {
        return capacity;
    }

    /**
     * @param capacity riders carried per cycle
     * @throws IllegalArgumentException when not positive
     */
    public void setCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
    }

    /** {@inheritDoc} */
    @Override
    public double getMinHeight() {
        return minHeight;
    }

    /**
     * @param minHeight safety minimum in metres
     * @throws IllegalArgumentException when negative
     */
    public void setMinHeight(double minHeight) {
        if (minHeight < 0.0) {
            throw new IllegalArgumentException("Minimum height cannot be negative");
        }
        this.minHeight = minHeight;
    }

    /** {@inheritDoc} */
    @Override
    public synchronized double getWear() {
        return wear;
    }

    /**
     * Sets wear, clamping into the range 0..100.
     *
     * @param wear the new wear percentage
     */
    public synchronized void setWear(double wear) {
        if (wear < 0.0) {
            this.wear = 0.0;
        } else if (wear > 100.0) {
            this.wear = 100.0;
        } else {
            this.wear = wear;
        }
    }

    /** {@inheritDoc} */
    @Override
    public synchronized double getWearRate() {
        return wearRate;
    }

    /**
     * @param wearRate wear added per completed cycle
     * @throws IllegalArgumentException when negative
     */
    public synchronized void setWearRate(double wearRate) {
        if (wearRate < 0.0) {
            throw new IllegalArgumentException("Wear rate cannot be negative");
        }
        this.wearRate = wearRate;
    }

    /** @return one of the {@code STATUS_*} constants */
    public synchronized int getStatus() {
        return status;
    }

    /** @param status one of the {@code STATUS_*} constants */
    public synchronized void setStatus(int status) {
        this.status = status;
    }

    /** @return a human-readable status name */
    public synchronized String getStatusString() {
        switch (status) {
            case STATUS_OPEN: return "Open";
            case STATUS_RUNNING: return "Running";
            case STATUS_UNDER_MAINTENANCE: return "Maintenance";
            case STATUS_CLOSED: return "Closed";
            default: return "Unknown";
        }
    }

    /** @return {@code true} when the ride can currently take a booking */
    public synchronized boolean isBookable() {
        return status == STATUS_OPEN || status == STATUS_RUNNING;
    }

    /** @return total riders carried since the park opened */
    public synchronized int getRidersServed() {
        return ridersServed;
    }

    /** @param count riders to add to the running total */
    public synchronized void addRidersServed(int count) {
        this.ridersServed += count;
    }

    /** @return total revenue attributed to this ride */
    public synchronized double getRevenue() {
        return revenue;
    }

    /** @param amount revenue to add to the running total */
    public synchronized void addRevenue(double amount) {
        this.revenue += amount;
    }

    /** @return a defensive snapshot of the waiting queue */
    public synchronized List<Visitor> getQueue() {
        return Collections.unmodifiableList(new ArrayList<>(queue));
    }

    /** {@inheritDoc} */
    @Override
    public synchronized int getQueueSize() {
        return queue.size();
    }

    /**
     * Adds a visitor to the waiting queue.
     *
     * <p>VIP Fast Pass holders are inserted ahead of standard visitors but behind
     * any VIP already waiting, so VIPs keep their own arrival order.</p>
     *
     * @param visitor the visitor to enqueue; {@code null} is ignored
     */
    public synchronized void enqueueVisitor(Visitor visitor) {
        if (visitor == null) {
            return;
        }
        if (visitor.getTicket() != null && visitor.getTicket().getTicketType() == Ticket.TYPE_VIP) {
            int insertIndex = 0;
            for (int i = 0; i < queue.size(); i++) {
                Visitor qv = queue.get(i);
                if (qv.getTicket() != null && qv.getTicket().getTicketType() == Ticket.TYPE_VIP) {
                    insertIndex = i + 1;
                } else {
                    break;
                }
            }
            queue.add(insertIndex, visitor);
        } else {
            queue.add(visitor);
        }
    }

    /**
     * Removes up to {@code count} visitors from the front of the queue.
     *
     * @param count how many riders to load
     * @return the loaded riders, possibly fewer than requested
     */
    public synchronized List<Visitor> dequeueRiders(int count) {
        List<Visitor> riders = new ArrayList<>();
        int toLoad = Math.min(count, queue.size());
        for (int i = 0; i < toLoad; i++) {
            riders.add(queue.remove(0));
        }
        return riders;
    }

    /**
     * Cancels one visitor's place in the queue (FR-05).
     *
     * @param visitorName the visitor to remove, matched case-insensitively
     * @return the removed visitor, or {@code null} when nobody matched
     */
    public synchronized Visitor cancelQueuedVisitor(String visitorName) {
        if (visitorName == null) {
            return null;
        }
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getName().equalsIgnoreCase(visitorName)) {
                return queue.remove(i);
            }
        }
        return null;
    }

    /**
     * Atomically claims a load of riders and marks the ride
     * {@link #STATUS_RUNNING}.
     *
     * <p>The caller must then sleep for the cycle duration <em>without</em>
     * holding this lock, and finish with {@link #completeCycle(int)}.</p>
     *
     * @return the riders loaded; empty when the ride could not start a cycle
     */
    public synchronized List<Visitor> beginCycle() {
        if (status != STATUS_OPEN || queue.isEmpty()) {
            return new ArrayList<>();
        }
        List<Visitor> riders = dequeueRiders(capacity);
        if (!riders.isEmpty()) {
            status = STATUS_RUNNING;
        }
        return riders;
    }

    /**
     * Records the results of a finished cycle and adds the wear it caused.
     *
     * @param riderCount how many riders completed the cycle
     * @return the ride's new wear percentage
     */
    public synchronized double completeCycle(int riderCount) {
        ridersServed += riderCount;
        cyclesRun++;
        setWear(wear + wearRate);
        return wear;
    }

    /** @return how many cycles this ride has run */
    public synchronized int getCyclesRun() {
        return cyclesRun;
    }

    /**
     * Load factor: how full the ride actually ran, as a percentage of the seats
     * it offered.
     *
     * <p>A ride that always departs full scores 100. A big Ferris wheel cycling
     * with two people aboard scores low even if its revenue looks healthy, which
     * is exactly the inefficiency management wants to see.</p>
     *
     * @return riders carried as a percentage of seats offered, 0 when never run
     */
    public synchronized double getLoadFactorPercent() {
        if (cyclesRun == 0) {
            return 0.0;
        }
        double offered = (double) cyclesRun * capacity;
        return Math.min(100.0, 100.0 * ridersServed / offered);
    }

    // ------------------------------------------------------- availability

    /**
     * Starts (or restarts) the availability measurement window.
     *
     * <p>Called from the constructor and again by {@code Main} after a ride is
     * restored from disk, so uptime always describes the current run.</p>
     */
    public synchronized void beginUptimeWindow() {
        uptimeWindowStart = System.currentTimeMillis();
        totalOfflineMs = 0L;
        offlineSince = isBookable() ? 0L : uptimeWindowStart;
    }

    /** Opens an offline interval. Caller holds this monitor. */
    private void markOffline() {
        if (offlineSince == 0L) {
            offlineSince = System.currentTimeMillis();
        }
    }

    /** Closes an open offline interval. Caller holds this monitor. */
    private void markInService() {
        if (offlineSince != 0L) {
            totalOfflineMs += System.currentTimeMillis() - offlineSince;
            offlineSince = 0L;
        }
    }

    /**
     * @return total milliseconds spent under maintenance or closed during this
     *         run, including any interval still open
     */
    public synchronized long getTotalOfflineMs() {
        long open = offlineSince == 0L ? 0L : System.currentTimeMillis() - offlineSince;
        return totalOfflineMs + open;
    }

    /**
     * @return the share of this run the ride has been available to visitors,
     *         as a percentage
     */
    public synchronized double getUptimePercent() {
        long window = System.currentTimeMillis() - uptimeWindowStart;
        if (window <= 0L) {
            return 100.0;
        }
        double up = 100.0 * (1.0 - ((double) getTotalOfflineMs() / window));
        return Math.max(0.0, Math.min(100.0, up));
    }

    /**
     * @return offline time formatted as {@code "1m 20s"}
     */
    public synchronized String getDowntimeString() {
        long seconds = getTotalOfflineMs() / 1000L;
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }

    /**
     * Returns the ride to service, but only if it is still
     * {@link #STATUS_RUNNING}.
     *
     * <p>Guarded so that a forced inspection raised while the cycle was in
     * progress is not silently overwritten.</p>
     *
     * @return {@code true} when the ride was reopened
     */
    public synchronized boolean reopenIfRunning() {
        if (status == STATUS_RUNNING) {
            status = STATUS_OPEN;
            return true;
        }
        return false;
    }

    /**
     * Takes the ride offline for repair.
     *
     * @return {@code true} when this call changed the status, {@code false} when
     *         the ride was already under maintenance
     */
    public synchronized boolean takeOffline() {
        if (status == STATUS_UNDER_MAINTENANCE) {
            return false;
        }
        status = STATUS_UNDER_MAINTENANCE;
        markOffline();
        return true;
    }

    /**
     * Resets wear to zero and reopens the ride after a completed repair.
     */
    public synchronized void returnToService() {
        this.wear = 0.0;
        this.status = STATUS_OPEN;
        markInService();
    }

    /**
     * Closes the ride by management decision (FR-09).
     *
     * <p>Distinct from maintenance: no task is raised, no technician is needed,
     * and wear is left untouched. A ride mid-cycle is allowed to finish — the
     * status change means {@link #reopenIfRunning()} will decline to reopen it.</p>
     *
     * @return {@code true} when this call changed the status
     */
    public synchronized boolean closeByManagement() {
        if (status == STATUS_CLOSED) {
            return false;
        }
        status = STATUS_CLOSED;
        markOffline();
        return true;
    }

    /**
     * Reopens a ride that management closed.
     *
     * <p>Refuses to touch a ride that is under maintenance — only a completed
     * repair may bring one of those back.</p>
     *
     * @return {@code true} when the ride was reopened
     */
    public synchronized boolean reopenFromClosed() {
        if (status != STATUS_CLOSED) {
            return false;
        }
        status = STATUS_OPEN;
        markInService();
        return true;
    }

    @Override
    public String toString() {
        return getRideType() + " '" + name + "' [" + zone + "] "
               + getStatusString() + " wear=" + String.format("%.1f%%", getWear());
    }
}
