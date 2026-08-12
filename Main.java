import javafx.application.Application;
import java.util.ArrayList;
import java.util.List;

/**
 * Composition root: restores the park, wires up every thread, and launches the
 * GUI.
 *
 * <h2>Startup order (§10.2 of the design document)</h2>
 * <ol>
 *   <li>Try the serialized snapshot — the fastest and most complete restore.</li>
 *   <li>Fall back to the CSV files.</li>
 *   <li>Fall back to hardcoded defaults so the app runs on a clean machine.</li>
 * </ol>
 *
 * <h2>Thread inventory</h2>
 * <p>1 park clock + one cycle thread per ride + 3 ticket counters + 2 maintenance
 * crews + 1 auto-save. With the default seven rides that is fourteen live
 * threads, plus the audit logger's daemon writer.</p>
 */
public class Main {

    /** Number of simulated ticket windows. */
    private static final int TICKET_COUNTERS = 3;

    /** Number of simulated maintenance crews. */
    private static final int MAINTENANCE_CREWS = 2;

    private static final List<Thread> backgroundThreads = new ArrayList<>();
    private static final List<Runnable> runnables = new ArrayList<>();

    private Main() {
        // application entry point only
    }

    /**
     * Application entry point.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        ParkState parkState = restorePark();
        ensureTechnicians(parkState);
        startSimulation(parkState);
        registerShutdownHook(parkState);

        // Hands the model to the JavaFX layer, then blocks here until the window
        // is closed. JavaFX builds the Application itself, so the park has to be
        // passed through a static rather than a constructor.
        ParkApplication.setContext(parkState, parkClock);
        Application.launch(ParkApplication.class, args);
    }

    /**
     * Restores the park using the three-tier fallback.
     *
     * @return a populated park, never {@code null}
     */
    private static ParkState restorePark() {
        ParkState restored = ParkState.loadState(AutoSaveService.STATE_FILE);
        if (restored != null) {
            AuditLogger.getInstance().log("SYSTEM", "Park state loaded successfully from serialization.");
            restored.syncIdCounters();
            restored.beginUptimeWindows();
            return restored;
        }

        AuditLogger.getInstance().log("SYSTEM",
            "Serialized state not found. Attempting to load from CSV...");
        ParkState parkState = new ParkState();

        List<Ride> rides = CSVHandler.loadRides(AutoSaveService.RIDES_FILE);
        if (rides.isEmpty()) {
            AuditLogger.getInstance().log("SYSTEM", "No rides CSV found. Initializing default rides...");
            rides = defaultRides();
        }
        parkState.setRides(rides);

        List<Ticket> tickets = CSVHandler.loadTickets(AutoSaveService.TICKETS_FILE);
        for (Ticket t : tickets) {
            parkState.addSoldTicket(t);
            parkState.addRevenue(t.getCurrentPrice());
        }

        for (Visitor v : CSVHandler.loadVisitors(AutoSaveService.VISITORS_FILE, tickets)) {
            parkState.registerVisitor(v);
        }

        // Re-seed the ID counters so freshly sold tickets cannot reuse a
        // restored ticket's identifier.
        parkState.syncIdCounters();
        parkState.beginUptimeWindows();
        return parkState;
    }

    /**
     * @return the park's default ride roster, one per zone theme
     */
    private static List<Ride> defaultRides() {
        List<Ride> rides = new ArrayList<>();
        rides.add(new RollerCoaster("Cyclone Coaster", "Coaster Zone"));
        rides.add(new RollerCoaster("Titan Coaster", "Coaster Zone"));
        rides.add(new WaterSlide("Wave Runner", "Splash Zone"));
        rides.add(new WaterSlide("Aqua Drop", "Splash Zone"));
        rides.add(new ZipLine("Giga Zip Line", "Sky Zone"));
        rides.add(new FerrisWheel("Sky Wheel", "Sky Zone"));
        rides.add(new ClimbingWall("Summit Wall", "Adventure Zone"));
        return rides;
    }

    /**
     * Hires the default technician roster if the park has none.
     *
     * @param parkState the park to staff
     */
    private static void ensureTechnicians(ParkState parkState) {
        if (!parkState.getTechnicians().isEmpty()) {
            return;
        }
        AuditLogger.getInstance().log("SYSTEM", "Initializing default technicians...");
        parkState.addTechnician(new Technician("Jack", 22, "T-01", Technician.LEVEL_JUNIOR));
        parkState.addTechnician(new Technician("Jane", 23, "T-02", Technician.LEVEL_JUNIOR));
        parkState.addTechnician(new Technician("Mike", 28, "T-03", Technician.LEVEL_MID));
        parkState.addTechnician(new Technician("Mary", 29, "T-04", Technician.LEVEL_MID));
        parkState.addTechnician(new Technician("Sam", 35, "T-05", Technician.LEVEL_SENIOR));
        parkState.addTechnician(new Technician("Sarah", 36, "T-06", Technician.LEVEL_SENIOR));
    }

    private static ParkClock parkClock;

    /**
     * Creates and starts every simulation thread.
     *
     * @param parkState the park the threads operate on
     */
    private static void startSimulation(ParkState parkState) {
        AuditLogger.getInstance().log("SYSTEM", "Spawning background simulation threads...");

        parkClock = new ParkClock(parkState);
        register(parkClock, new Thread(parkClock, "ParkClockThread"));

        for (Ride ride : parkState.getRides()) {
            RideCycle cycle = new RideCycle(ride, parkState);
            register(cycle, new Thread(cycle, "RideCycle-" + ride.getName()));
        }

        for (int i = 1; i <= TICKET_COUNTERS; i++) {
            TicketCounter counter = new TicketCounter("Counter-" + i, parkState);
            register(counter, new Thread(counter, "TicketCounter-" + i));
        }

        for (int i = 1; i <= MAINTENANCE_CREWS; i++) {
            MaintenanceCrew crew = new MaintenanceCrew("Crew-" + i, parkState);
            register(crew, new Thread(crew, "MaintenanceCrew-" + i));
        }

        AutoSaveService autoSave = new AutoSaveService(parkState);
        register(autoSave, new Thread(autoSave, "AutoSaveThread"));

        for (Thread t : backgroundThreads) {
            t.start();
        }
        AuditLogger.getInstance().log("SYSTEM",
            backgroundThreads.size() + " simulation threads started.");
    }

    /**
     * @param runnable the task, kept so it can be asked to stop later
     * @param thread   the thread carrying it
     */
    private static void register(Runnable runnable, Thread thread) {
        runnables.add(runnable);
        backgroundThreads.add(thread);
    }

    /**
     * Registers a JVM hook that stops the threads and force-saves on exit.
     *
     * @param parkState the park to save
     */
    private static void registerShutdownHook(final ParkState parkState) {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                shutdownSimulation(parkState);
            }
        }, "ShutdownHook"));
    }

    /**
     * Asks every task to stop, interrupts the threads and writes a final save.
     *
     * @param parkState the park to save
     */
    private static void shutdownSimulation(ParkState parkState) {
        System.out.println("Stopping simulation threads...");
        for (Runnable r : runnables) {
            if (r instanceof ParkClock) {
                ((ParkClock) r).stopClock();
            } else if (r instanceof RideCycle) {
                ((RideCycle) r).stopCycle();
            } else if (r instanceof TicketCounter) {
                ((TicketCounter) r).stopCounter();
            } else if (r instanceof MaintenanceCrew) {
                ((MaintenanceCrew) r).stopCrew();
            } else if (r instanceof AutoSaveService) {
                ((AutoSaveService) r).stopService();
            }
        }
        for (Thread t : backgroundThreads) {
            t.interrupt();
        }
        AutoSaveService.saveAll(parkState);
        System.out.println("Simulation threads halted and final state saved.");
    }
}
