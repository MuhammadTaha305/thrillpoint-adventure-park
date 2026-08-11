/**
 * Background thread that snapshots the park to disk every ten seconds (FR-14).
 *
 * <p>Writes the serialized snapshot through
 * {@link ParkState#saveQuietly(String)}, which takes the {@link ParkState}
 * monitor so the object graph cannot be written half-updated. A transient disk
 * error is logged rather than thrown, so a full disk never kills the thread.</p>
 */
public class AutoSaveService implements Runnable {

    /** Interval between snapshots, in milliseconds. */
    private static final long SAVE_INTERVAL_MS = 10000L;

    /** Default path of the serialized snapshot. */
    public static final String STATE_FILE = "park_state.ser";

    /** Default path of the rides CSV. */
    public static final String RIDES_FILE = "rides.csv";

    /** Default path of the tickets CSV. */
    public static final String TICKETS_FILE = "tickets.csv";

    /** Default path of the visitors CSV. */
    public static final String VISITORS_FILE = "visitors.csv";

    private final ParkState parkState;
    private boolean running;

    /**
     * @param parkState the park to snapshot
     */
    public AutoSaveService(ParkState parkState) {
        this.parkState = parkState;
        this.running = true;
    }

    /** Asks this thread to stop before its next save. */
    public synchronized void stopService() {
        this.running = false;
    }

    /** @return {@code true} while this thread should keep saving */
    private synchronized boolean isRunning() {
        return running;
    }

    /**
     * Writes the snapshot and all three CSV files once.
     *
     * <p>Also used by the shutdown path so that exit and auto-save share exactly
     * one implementation.</p>
     *
     * @param parkState the park to write
     */
    public static void saveAll(ParkState parkState) {
        parkState.saveQuietly(STATE_FILE);
        CSVHandler.saveRidesQuietly(parkState.getRides(), RIDES_FILE);
        CSVHandler.saveTicketsQuietly(parkState.getSoldTickets(), TICKETS_FILE);
        CSVHandler.saveVisitorsQuietly(parkState.getRegisteredVisitors(), VISITORS_FILE);
    }

    @Override
    public void run() {
        AuditLogger.getInstance().log("SYSTEM", "Auto-Save Service thread started.");

        while (isRunning()) {
            if (!SimulationControl.getInstance().sleepScaled(SAVE_INTERVAL_MS)) {
                break;
            }

            saveAll(parkState);
            AuditLogger.getInstance().log("SYSTEM",
                "Auto-save completed: serialized park state and updated CSV files.");
        }

        AuditLogger.getInstance().log("SYSTEM", "Auto-Save Service thread stopped.");
    }
}
