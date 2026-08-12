import java.io.Serializable;

/**
 * One unit of maintenance work: repair ride X, performed by technician Y.
 *
 * <p>Tasks are created by {@link ParkState#raiseMaintenanceTask(Ride)} (automatic,
 * wear-triggered) or {@link ParkState#forceInspection(Ride)} (manager-triggered),
 * claimed by {@link MaintenanceCrew} and closed by
 * {@link ParkState#completeTask(MaintenanceTask)}.</p>
 *
 * <p>Mutators are {@code synchronized} because the maintenance crew threads write
 * them while the Swing thread reads them for display. They are always called
 * while the caller already holds the {@link ParkState} lock; a task lock is never
 * held while acquiring any other lock.</p>
 */
public class MaintenanceTask implements Serializable, Identifiable {
    private static final long serialVersionUID = 2L;

    /** Raised, nobody assigned yet. */
    public static final int STATUS_PENDING = 0;

    /** A technician is on site. */
    public static final int STATUS_IN_PROGRESS = 1;

    /** Repair finished, ride returned to service. */
    public static final int STATUS_COMPLETED = 2;

    private String taskId;
    private Ride ride;
    private Technician technician;
    private int status;
    private long raisedTime;

    /**
     * @param taskId unique identifier, e.g. {@code "MNT-7"}
     * @param ride   the ride needing work
     */
    public MaintenanceTask(String taskId, Ride ride) {
        setTaskId(taskId);
        setRide(ride);
        this.status = STATUS_PENDING;
        this.raisedTime = System.currentTimeMillis();
    }

    /** @return the unique task identifier */
    public String getTaskId() {
        return taskId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Satisfies {@link Identifiable} by delegating to {@link #getTaskId()}.</p>
     */
    @Override
    public String getId() {
        return taskId;
    }

    /**
     * @param taskId the unique identifier
     * @throws IllegalArgumentException when blank
     */
    public void setTaskId(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("Task ID cannot be empty");
        }
        this.taskId = taskId;
    }

    /** @return the ride this task targets */
    public Ride getRide() {
        return ride;
    }

    /**
     * @param ride the ride this task targets
     * @throws IllegalArgumentException when {@code null}
     */
    public void setRide(Ride ride) {
        if (ride == null) {
            throw new IllegalArgumentException("Ride cannot be null");
        }
        this.ride = ride;
    }

    /** @return the assigned technician, or {@code null} while pending */
    public synchronized Technician getTechnician() {
        return technician;
    }

    /** @param technician the technician taking the job */
    public synchronized void setTechnician(Technician technician) {
        this.technician = technician;
    }

    /** @return one of the {@code STATUS_*} constants */
    public synchronized int getStatus() {
        return status;
    }

    /**
     * @param status one of the {@code STATUS_*} constants
     * @throws IllegalArgumentException when out of range
     */
    public synchronized void setStatus(int status) {
        if (status < STATUS_PENDING || status > STATUS_COMPLETED) {
            throw new IllegalArgumentException("Invalid task status");
        }
        this.status = status;
    }

    /** @return {@code true} while this task still needs work */
    public synchronized boolean isActive() {
        return status != STATUS_COMPLETED;
    }

    /** @return a human-readable status name */
    public synchronized String getStatusString() {
        switch (status) {
            case STATUS_PENDING: return "Pending";
            case STATUS_IN_PROGRESS: return "In Progress";
            case STATUS_COMPLETED: return "Completed";
            default: return "Unknown";
        }
    }

    /** @return when this task was raised, in milliseconds since the epoch */
    public long getRaisedTime() {
        return raisedTime;
    }

    @Override
    public String toString() {
        return taskId + " -> " + ride.getName() + " (" + getStatusString() + ")";
    }
}
