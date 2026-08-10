import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The aggregate root: every piece of park-wide shared state lives here.
 *
 * <h2>Locking contract</h2>
 * <p>The park-wide lock order is <strong>Ride, then ParkState, then Ticket</strong>.
 * Any operation that touches both a ride and this object must take the ride's
 * monitor <em>first</em>. That is why {@link #raiseMaintenanceTask(Ride)},
 * {@link #forceInspection(Ride)} and {@link #completeTask(MaintenanceTask)} are
 * <em>not</em> declared {@code synchronized} — they open the ride monitor and only
 * then the {@code ParkState} monitor. Declaring them {@code synchronized} would
 * reverse the order against {@link TicketingService} and deadlock the park.</p>
 *
 * <h2>Identifier generation</h2>
 * <p>Ticket and task identifiers come from {@link #nextTicketId()} and
 * {@link #nextTaskId()}, which are {@code synchronized} on this object. Deriving
 * an ID from {@code list.size()} while holding only a ride lock lets two counters
 * mint the same ID, so it is never done.</p>
 */
public class ParkState implements Serializable {
    private static final long serialVersionUID = 3L;

    /**
     * Rides live inside zones, so {@link Zone} is the single source of truth for
     * the ride roster and {@link #getRides()} flattens it.
     */
    private List<Zone> zones;
    private List<Visitor> registeredVisitors;
    private List<Ticket> soldTickets;
    private List<Technician> technicians;
    private List<MaintenanceTask> maintenanceTasks;
    private List<String> safetyLogs;
    private List<DayRecord> dayHistory;
    private int currentPeriod;
    private double totalRevenue;
    private int totalRidersServed;
    private int ticketCounter;
    private int taskCounter;
    private int repairsCompleted;

    // Cumulative readings at the last day close, so closeDay() can report deltas.
    private double revenueAtLastClose;
    private int ridersAtLastClose;
    private int ticketsAtLastClose;
    private int repairsAtLastClose;

    /** Creates an empty park. */
    public ParkState() {
        zones = new ArrayList<>();
        registeredVisitors = new ArrayList<>();
        soldTickets = new ArrayList<>();
        technicians = new ArrayList<>();
        maintenanceTasks = new ArrayList<>();
        safetyLogs = new ArrayList<>();
        dayHistory = new ArrayList<>();
        currentPeriod = PricingService.PERIOD_STANDARD;
        totalRevenue = 0.0;
        totalRidersServed = 0;
        ticketCounter = 0;
        taskCounter = 0;
        repairsCompleted = 0;
        revenueAtLastClose = 0.0;
        ridersAtLastClose = 0;
        ticketsAtLastClose = 0;
        repairsAtLastClose = 0;
    }

    // ---------------------------------------------------------- zones & rides

    /** @return a defensive snapshot of the zone list */
    public synchronized List<Zone> getZones() {
        return new ArrayList<>(zones);
    }

    /**
     * @param name the zone's name, matched case-insensitively
     * @return the matching zone, or {@code null} when there is none
     */
    public synchronized Zone findZoneByName(String name) {
        if (name == null) {
            return null;
        }
        for (Zone z : zones) {
            if (z.getName().equalsIgnoreCase(name)) {
                return z;
            }
        }
        return null;
    }

    /**
     * @return every ride in the park, flattened across zones; a defensive copy
     */
    public synchronized List<Ride> getRides() {
        List<Ride> all = new ArrayList<>();
        for (Zone z : zones) {
            all.addAll(z.getRides());
        }
        return all;
    }

    /**
     * Installs a ride roster, grouping the rides into zones by their zone name.
     *
     * @param newRides the rides to install
     */
    public synchronized void setRides(List<Ride> newRides) {
        zones = new ArrayList<>();
        for (Ride r : newRides) {
            addRideInternal(r);
        }
    }

    /**
     * Adds one ride, creating its zone if this is the first ride in it.
     *
     * @param ride the ride to add; {@code null} is ignored
     */
    public synchronized void addRide(Ride ride) {
        addRideInternal(ride);
    }

    /**
     * Must be called while holding this object's monitor.
     *
     * @param ride the ride to file into its zone
     */
    private void addRideInternal(Ride ride) {
        if (ride == null) {
            return;
        }
        Zone zone = findZoneByName(ride.getZone());
        if (zone == null) {
            zone = new Zone(ride.getZone());
            zones.add(zone);
        }
        zone.addRide(ride);
    }

    /**
     * @param name the ride's display name, matched case-insensitively
     * @return the matching ride, or {@code null} when there is none
     */
    public synchronized Ride findRideByName(String name) {
        if (name == null) {
            return null;
        }
        for (Zone z : zones) {
            for (Ride r : z.getRides()) {
                if (r.getName().equalsIgnoreCase(name)) {
                    return r;
                }
            }
        }
        return null;
    }

    /**
     * Restarts the availability window on every ride, so uptime describes the
     * current run rather than a stale measurement restored from disk.
     *
     * <p><strong>Call this during startup, before the simulation threads are
     * started.</strong> It is the one method that walks {@code ParkState -> Zone
     * -> Ride}, the reverse of the park's normal lock order. That is safe only
     * while this thread is the only one running.</p>
     */
    public synchronized void beginUptimeWindows() {
        for (Zone z : zones) {
            for (Ride r : z.getRides()) {
                r.beginUptimeWindow();
            }
        }
    }

    /**
     * Closes a ride by management decision (FR-09).
     *
     * <p>Ride monitor first, then this object's, per the park-wide lock order.</p>
     *
     * @param ride the ride to close
     * @return {@code true} when the ride was closed by this call
     */
    public boolean closeRide(Ride ride) {
        if (ride == null || !ride.closeByManagement()) {
            return false;
        }
        AuditLogger.getInstance().log("MANAGER",
            "Manager closed " + ride.getName() + ". It will accept no further bookings.");
        return true;
    }

    /**
     * Reopens a ride that management closed.
     *
     * @param ride the ride to reopen
     * @return {@code true} when the ride was reopened by this call
     */
    public boolean reopenRide(Ride ride) {
        if (ride == null || !ride.reopenFromClosed()) {
            return false;
        }
        AuditLogger.getInstance().log("MANAGER",
            "Manager reopened " + ride.getName() + ". It is accepting bookings again.");
        return true;
    }

    // ------------------------------------------------------------- visitors

    /** @return a defensive snapshot of every registered visitor */
    public synchronized List<Visitor> getRegisteredVisitors() {
        return new ArrayList<>(registeredVisitors);
    }

    /**
     * Records a visitor.
     *
     * <p>Deliberately does <em>not</em> de-duplicate by name. Two different
     * visitors can share a name, and the same person can return later with a new
     * ticket; silently dropping either corrupts the visitor register and the
     * revenue reports built from it.</p>
     *
     * @param visitor the visitor to record; {@code null} is ignored
     */
    public synchronized void registerVisitor(Visitor visitor) {
        if (visitor != null) {
            registeredVisitors.add(visitor);
        }
    }

    /**
     * Removes a visitor from the register, used when a booking is cancelled.
     *
     * @param visitor the exact visitor instance to remove
     * @return {@code true} when the visitor was present
     */
    public synchronized boolean unregisterVisitor(Visitor visitor) {
        return visitor != null && registeredVisitors.remove(visitor);
    }

    // -------------------------------------------------------------- tickets

    /** @return a defensive snapshot of every ticket sold */
    public synchronized List<Ticket> getSoldTickets() {
        return new ArrayList<>(soldTickets);
    }

    /** @param ticket the ticket to record; {@code null} is ignored */
    public synchronized void addSoldTicket(Ticket ticket) {
        if (ticket != null) {
            soldTickets.add(ticket);
        }
    }

    /**
     * Removes a ticket, used when a booking is cancelled.
     *
     * @param ticket the exact ticket instance to remove
     * @return {@code true} when the ticket was present
     */
    public synchronized boolean removeSoldTicket(Ticket ticket) {
        return ticket != null && soldTickets.remove(ticket);
    }

    /**
     * Mints the next unique ticket identifier.
     *
     * <p>Atomic with respect to every other counter thread, which is the whole
     * point: three {@link TicketCounter} threads selling on three different rides
     * hold three different ride locks and would otherwise read the same list size
     * and mint the same ID.</p>
     *
     * @return an identifier of the form {@code "TCK-42"}
     */
    public synchronized String nextTicketId() {
        ticketCounter++;
        return "TCK-" + ticketCounter;
    }

    /**
     * Mints the next unique maintenance task identifier.
     *
     * @return an identifier of the form {@code "MNT-7"}
     */
    public synchronized String nextTaskId() {
        taskCounter++;
        return "MNT-" + taskCounter;
    }

    /**
     * Re-seeds the ID counters after data is restored from CSV, so that reloaded
     * tickets do not collide with newly sold ones.
     */
    public synchronized void syncIdCounters() {
        if (ticketCounter < soldTickets.size()) {
            ticketCounter = soldTickets.size();
        }
        if (taskCounter < maintenanceTasks.size()) {
            taskCounter = maintenanceTasks.size();
        }
    }

    /**
     * Expires every ticket in the park (FR-11), called by {@link ParkClock} when
     * the park closes for the day.
     *
     * @return how many tickets were expired by this call
     */
    public int expireAllTickets() {
        List<Ticket> snapshot = getSoldTickets();
        int expired = 0;
        for (Ticket t : snapshot) {
            if (!t.isExpired()) {
                t.expire();
                expired++;
            }
        }
        return expired;
    }

    // ---------------------------------------------------------- technicians

    /** @return a defensive snapshot of the technician roster */
    public synchronized List<Technician> getTechnicians() {
        return new ArrayList<>(technicians);
    }

    /** @param tech the technician to hire; {@code null} is ignored */
    public synchronized void addTechnician(Technician tech) {
        if (tech != null) {
            technicians.add(tech);
        }
    }

    // ---------------------------------------------------------- maintenance

    /** @return a defensive snapshot of the maintenance task list */
    public synchronized List<MaintenanceTask> getMaintenanceTasks() {
        return new ArrayList<>(maintenanceTasks);
    }

    /**
     * Must be called while holding this object's monitor.
     *
     * @param ride the ride to check
     * @return {@code true} when an unfinished task already targets {@code ride}
     */
    private boolean hasActiveTaskFor(Ride ride) {
        for (MaintenanceTask task : maintenanceTasks) {
            if (task.getRide().getName().equalsIgnoreCase(ride.getName()) && task.isActive()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Raises a wear-triggered maintenance task and takes the ride offline.
     *
     * <p>Not {@code synchronized} — it acquires the <em>ride</em> monitor first and
     * this object's monitor second, honouring the park-wide lock order.</p>
     *
     * @param ride the worn ride
     * @return {@code true} when a new task was raised, {@code false} when the ride
     *         already had one outstanding
     */
    public boolean raiseMaintenanceTask(Ride ride) {
        if (ride == null) {
            return false;
        }
        String logMessage = null;
        synchronized (ride) {
            synchronized (this) {
                if (hasActiveTaskFor(ride)) {
                    return false;
                }
                MaintenanceTask task = new MaintenanceTask(nextTaskId(), ride);
                maintenanceTasks.add(task);
                logMessage = "Maintenance task " + task.getTaskId() + " raised automatically for "
                             + ride.getName() + " due to excessive wear ("
                             + String.format("%.1f", ride.getWear()) + "%).";
            }
            ride.takeOffline();
        }
        AuditLogger.getInstance().log("SYSTEM", logMessage);
        return true;
    }

    /**
     * Raises a manager-requested inspection (FR-09).
     *
     * <p>Uses the same duplicate guard as the automatic path, so clicking the
     * button twice cannot put two technicians on the same ride. Takes the ride
     * monitor first, so a cycle that is mid-flight finishes and then finds itself
     * unable to reopen — see {@link Ride#reopenIfRunning()}.</p>
     *
     * @param ride the ride to inspect
     * @return {@code true} when a new inspection was raised, {@code false} when
     *         the ride was already scheduled for work
     */
    public boolean forceInspection(Ride ride) {
        if (ride == null) {
            return false;
        }
        String logMessage = null;
        synchronized (ride) {
            synchronized (this) {
                if (hasActiveTaskFor(ride)) {
                    return false;
                }
                MaintenanceTask task = new MaintenanceTask(nextTaskId(), ride);
                maintenanceTasks.add(task);
                logMessage = "Manager forced inspection " + task.getTaskId() + " on " + ride.getName()
                             + " (was " + ride.getStatusString() + "). Ride is now UNDER_MAINTENANCE.";
            }
            ride.takeOffline();
        }
        AuditLogger.getInstance().log("MANAGER", logMessage);
        return true;
    }

    /**
     * Certification rule: which technician is allowed to work on which ride.
     *
     * @param tech the candidate technician
     * @param ride the ride needing work
     * @return {@code true} when {@code tech} is qualified
     */
    public boolean canTechnicianRepair(Technician tech, Ride ride) {
        if (ride instanceof RollerCoaster) {
            return tech.getCertificationLevel() == Technician.LEVEL_SENIOR;
        } else if (ride instanceof WaterSlide || ride instanceof ZipLine) {
            return tech.getCertificationLevel() >= Technician.LEVEL_MID;
        } else {
            return tech.getCertificationLevel() >= Technician.LEVEL_JUNIOR;
        }
    }

    /**
     * Atomically finds a pending task this technician is qualified for and
     * assigns it.
     *
     * @param tech the idle technician looking for work
     * @return the claimed task, or {@code null} when there is nothing suitable
     */
    public synchronized MaintenanceTask claimNextTask(Technician tech) {
        if (tech == null || tech.isBusy()) {
            return null;
        }
        for (MaintenanceTask task : maintenanceTasks) {
            if (task.getStatus() == MaintenanceTask.STATUS_PENDING
                && canTechnicianRepair(tech, task.getRide())) {
                task.setTechnician(tech);
                task.setStatus(MaintenanceTask.STATUS_IN_PROGRESS);
                tech.setBusy(true);
                return task;
            }
        }
        return null;
    }

    /**
     * Closes a finished repair: frees the technician, resets wear and reopens the
     * ride.
     *
     * <p>Not {@code synchronized} — ride monitor first, then this object's.</p>
     *
     * @param task the completed task; {@code null} is ignored
     */
    public void completeTask(MaintenanceTask task) {
        if (task == null) {
            return;
        }
        Ride ride = task.getRide();
        String logEntry = null;
        if (ride == null) {
            synchronized (this) {
                task.setStatus(MaintenanceTask.STATUS_COMPLETED);
            }
            return;
        }
        synchronized (ride) {
            synchronized (this) {
                task.setStatus(MaintenanceTask.STATUS_COMPLETED);
                Technician tech = task.getTechnician();
                if (tech != null) {
                    tech.recordRepairCompleted();
                    tech.setBusy(false);
                }
                repairsCompleted++;
                ride.returnToService();
                logEntry = "Safety inspection completed for " + ride.getName()
                           + " by " + (tech != null ? tech.getName() : "Unknown")
                           + " (Level: " + (tech != null ? tech.getCertificationLevelString() : "N/A")
                           + "). Wear reset to 0%.";
                safetyLogs.add(logEntry);
            }
        }
        AuditLogger.getInstance().log("MAINTENANCE", logEntry);
    }

    // --------------------------------------------------------------- logs

    /** @return a defensive snapshot of the safety log */
    public synchronized List<String> getSafetyLogs() {
        return new ArrayList<>(safetyLogs);
    }

    /** @param log a line to append to the safety log */
    public synchronized void addSafetyLog(String log) {
        safetyLogs.add(log);
    }

    // ------------------------------------------------------------ reporting

    /** @return the active pricing period index */
    public synchronized int getCurrentPeriod() {
        return currentPeriod;
    }

    /** @param currentPeriod the pricing period index to switch to */
    public synchronized void setCurrentPeriod(int currentPeriod) {
        this.currentPeriod = currentPeriod;
    }

    /** @return total revenue taken across the whole park */
    public synchronized double getTotalRevenue() {
        return totalRevenue;
    }

    /** @param amount revenue to add; negative values refund */
    public synchronized void addRevenue(double amount) {
        this.totalRevenue += amount;
    }

    /** @return total riders carried across the whole park */
    public synchronized int getTotalRidersServed() {
        return totalRidersServed;
    }

    /** @param count riders to add to the park-wide total */
    public synchronized void addRidersServed(int count) {
        this.totalRidersServed += count;
    }

    /** @return maintenance tasks closed since the park opened */
    public synchronized int getRepairsCompleted() {
        return repairsCompleted;
    }

    // ------------------------------------------------------------ day history

    /** @return a defensive snapshot of every completed operating day */
    public synchronized List<DayRecord> getDayHistory() {
        return new ArrayList<>(dayHistory);
    }

    /**
     * Closes the books on an operating day.
     *
     * <p>Park totals are cumulative, so this records the difference since the
     * previous close — otherwise every day would look identical to the running
     * total and no trend would be visible.</p>
     *
     * @param dayNumber the day being closed
     * @return the record that was filed
     */
    public synchronized DayRecord closeDay(int dayNumber) {
        double revenue = totalRevenue - revenueAtLastClose;
        int riders = totalRidersServed - ridersAtLastClose;
        int tickets = soldTickets.size() - ticketsAtLastClose;
        int repairs = repairsCompleted - repairsAtLastClose;

        DayRecord record = new DayRecord(dayNumber, revenue, riders, tickets, repairs);
        dayHistory.add(record);

        revenueAtLastClose = totalRevenue;
        ridersAtLastClose = totalRidersServed;
        ticketsAtLastClose = soldTickets.size();
        repairsAtLastClose = repairsCompleted;

        return record;
    }

    /** @return revenue taken so far during the day currently in progress */
    public synchronized double getRevenueToday() {
        return totalRevenue - revenueAtLastClose;
    }

    /** @return riders carried so far during the day currently in progress */
    public synchronized int getRidersToday() {
        return totalRidersServed - ridersAtLastClose;
    }

    // ---------------------------------------------------------- persistence

    /**
     * Serializes this park to disk from a consistent snapshot.
     *
     * <p>{@code synchronized} on <em>this instance</em>. The previous
     * {@code static synchronized} version locked {@code ParkState.class} instead,
     * which shares no monitor with the instance methods that mutate the lists — so
     * it gave no protection at all and could write a half-updated object graph.</p>
     *
     * <p>Only this object's monitor is taken, never a ride's, so the lock order is
     * not violated.</p>
     *
     * @param filepath where to write the snapshot
     * @throws DataPersistenceException when the write fails
     */
    public synchronized void saveTo(String filepath) throws DataPersistenceException {
        try (FileOutputStream fos = new FileOutputStream(filepath);
             ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(this);
        } catch (IOException e) {
            throw new DataPersistenceException(filepath, e);
        }
    }

    /**
     * Convenience wrapper that logs instead of throwing, for background threads
     * that must not die on a transient disk error.
     *
     * @param filepath where to write the snapshot
     * @return {@code true} when the snapshot was written
     */
    public boolean saveQuietly(String filepath) {
        try {
            saveTo(filepath);
            return true;
        } catch (DataPersistenceException e) {
            AuditLogger.getInstance().log("ERROR", "Failed to serialize park state: " + e.getMessage());
            return false;
        }
    }

    /**
     * Restores a park from a serialized snapshot.
     *
     * @param filepath the snapshot to read
     * @return the restored park, or {@code null} when there is no usable snapshot
     */
    public static ParkState loadState(String filepath) {
        try (FileInputStream fis = new FileInputStream(filepath);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            return (ParkState) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            AuditLogger.getInstance().log("WARNING",
                "No serialized state found or failed to load: " + e.getMessage());
            return null;
        }
    }
}
