import java.util.ArrayList;
import java.util.List;

/**
 * One thread per ride: loads the queue, runs a cycle, adds wear, and hands the
 * ride to maintenance when it wears out.
 *
 * <h2>Why the ride lock is not held across the cycle</h2>
 * <p>The cycle sleep simulates several seconds of ride time. Holding the ride
 * monitor for that whole period would block every ticket counter trying to book
 * the ride, and would block a manager's forced inspection — which in turn holds
 * the {@link ParkState} monitor, freezing the entire park.</p>
 *
 * <p>So the cycle is split into three short critical sections:
 * {@link Ride#beginCycle()} claims riders and flips the status,
 * the sleep happens with <em>no</em> lock held, and
 * {@link Ride#completeCycle(int)} books the results. The status is restored with
 * {@link Ride#reopenIfRunning()}, which refuses to overwrite a maintenance status
 * raised while the cycle was in flight.</p>
 */
public class RideCycle implements Runnable {

    /** How long one simulated cycle takes, in milliseconds. */
    private static final long CYCLE_DURATION_MS = 4000L;

    /** How long to wait before re-checking an idle or offline ride. */
    private static final long IDLE_POLL_MS = 2000L;

    private final Ride ride;
    private final ParkState parkState;
    private boolean running;

    /**
     * @param ride      the ride this thread operates
     * @param parkState the park, for the shared totals and the maintenance queue
     */
    public RideCycle(Ride ride, ParkState parkState) {
        this.ride = ride;
        this.parkState = parkState;
        this.running = true;
    }

    /** Asks this thread to finish its current cycle and stop. */
    public synchronized void stopCycle() {
        this.running = false;
    }

    /** @return {@code true} while this thread should keep cycling */
    private synchronized boolean isRunning() {
        return running;
    }

    /** @return the audit-log category for this ride */
    private String logCategory() {
        return "RIDE-" + ride.getName().toUpperCase().replace(" ", "_");
    }

    @Override
    public void run() {
        AuditLogger.getInstance().log("SYSTEM", "Ride cycle thread started for " + ride.getName() + ".");

        while (isRunning()) {

            // --- short critical section: claim riders, mark RUNNING -----------
            List<Visitor> riders = ride.beginCycle();

            if (riders.isEmpty()) {
                if (!sleepFor(IDLE_POLL_MS)) {
                    break;
                }
                continue;
            }

            // Gate check: expired day passes and spent single-ride tickets are
            // turned away here rather than silently carried (FR-11 / P3).
            List<Visitor> admitted = new ArrayList<>();
            for (Visitor v : riders) {
                try {
                    TicketingService.verifyBoarding(v, ride);
                    admitted.add(v);
                } catch (ParkException e) {
                    AuditLogger.getInstance().log(logCategory(),
                        "Turned away at the gate: " + v.getName() + " - " + e.getMessage());
                }
            }

            if (admitted.isEmpty()) {
                ride.reopenIfRunning();
                if (!sleepFor(IDLE_POLL_MS)) {
                    break;
                }
                continue;
            }

            int loaded = admitted.size();
            AuditLogger.getInstance().log(logCategory(),
                "Starting cycle with " + loaded + " riders: [" + describe(admitted) + "].");

            // --- no lock held while the cycle runs ----------------------------
            if (!sleepFor(CYCLE_DURATION_MS)) {
                ride.reopenIfRunning();
                break;
            }

            // --- short critical section: book the results ---------------------
            double newWear = ride.completeCycle(loaded);
            parkState.addRidersServed(loaded);

            for (Visitor v : admitted) {
                Ticket t = v.getTicket();
                if (t instanceof SingleRideTicket) {
                    ((SingleRideTicket) t).setUsed(true);
                }
            }

            AuditLogger.getInstance().log(logCategory(),
                "Cycle completed. Wear is now " + String.format("%.1f", newWear) + "%.");

            if (newWear >= Maintainable.WEAR_THRESHOLD) {
                // If a task already existed we still must not leave the ride
                // stuck in RUNNING - that used to strand the ride permanently.
                if (!parkState.raiseMaintenanceTask(ride)) {
                    ride.takeOffline();
                }
            } else {
                ride.reopenIfRunning();
            }

            if (!sleepFor(IDLE_POLL_MS)) {
                break;
            }
        }

        AuditLogger.getInstance().log("SYSTEM", "Ride cycle thread stopped for " + ride.getName() + ".");
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

    /**
     * @param riders the loaded riders
     * @return a comma-separated list of their names
     */
    private String describe(List<Visitor> riders) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < riders.size(); i++) {
            names.append(riders.get(i).getName());
            if (i < riders.size() - 1) {
                names.append(", ");
            }
        }
        return names.toString();
    }
}
