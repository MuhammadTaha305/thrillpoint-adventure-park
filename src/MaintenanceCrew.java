/**
 * A maintenance crew thread: finds an idle technician, claims a task they are
 * qualified for, performs the repair, and returns the ride to service.
 *
 * <p>Two of these run concurrently and contend for the same task queue, which is
 * why claiming is a single atomic {@link ParkState#claimNextTask(Technician)}
 * call rather than a find-then-assign pair.</p>
 */
public class MaintenanceCrew implements Runnable {

    /** How long a simulated repair takes, in milliseconds. */
    private static final long REPAIR_DURATION_MS = 5000L;

    /** How long to wait before looking for work again. */
    private static final long IDLE_POLL_MS = 2000L;

    private final String crewId;
    private final ParkState parkState;
    private boolean running;

    /**
     * @param crewId    display name, e.g. {@code "Crew-1"}
     * @param parkState the park, for the technician roster and task queue
     */
    public MaintenanceCrew(String crewId, ParkState parkState) {
        this.crewId = crewId;
        this.parkState = parkState;
        this.running = true;
    }

    /** Asks this thread to finish its current repair and stop. */
    public synchronized void stopCrew() {
        this.running = false;
    }

    /** @return {@code true} while this thread should keep working */
    private synchronized boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        AuditLogger.getInstance().log("SYSTEM", "Maintenance Crew " + crewId + " thread started.");

        while (isRunning()) {
            MaintenanceTask claimed = null;
            Technician assignee = null;

            for (Technician tech : parkState.getTechnicians()) {
                if (!tech.isBusy()) {
                    MaintenanceTask task = parkState.claimNextTask(tech);
                    if (task != null) {
                        claimed = task;
                        assignee = tech;
                        break;
                    }
                }
            }

            if (claimed == null) {
                if (!sleepFor(IDLE_POLL_MS)) {
                    break;
                }
                continue;
            }

            AuditLogger.getInstance().log("MAINTENANCE",
                "Crew " + crewId + " assigned task " + claimed.getTaskId() + " for "
                + claimed.getRide().getName() + " to Technician " + assignee.getName()
                + " (Level: " + assignee.getCertificationLevelString() + "). Repairing...");

            // Interrupted mid-repair: release the technician and the task so the
            // roster is not left permanently busy, then exit the outer loop.
            if (!sleepFor(REPAIR_DURATION_MS)) {
                parkState.completeTask(claimed);
                break;
            }

            parkState.completeTask(claimed);
            AuditLogger.getInstance().log("MAINTENANCE",
                "Crew " + crewId + " completed repair on " + claimed.getRide().getName()
                + ". Ride is now OPEN and wear reset to 0%.");
        }

        AuditLogger.getInstance().log("SYSTEM", "Maintenance Crew " + crewId + " thread stopped.");
    }

    /**
     * Sleeps for a simulated duration, honouring the global pause and speed
     * controls.
     *
     * @param millis how long to sleep at normal speed
     * @return {@code true} when the full sleep completed
     */
    private boolean sleepFor(long millis) {
        return SimulationControl.getInstance().sleepScaled(millis);
    }
}
