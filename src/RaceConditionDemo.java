import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates the race condition the rest of the system is built to avoid
 * (FR-18).
 *
 * <p>Runs the identical booking workload twice against the identical shared
 * counter — once with an unsynchronised {@code book()} and once with a
 * {@code synchronized} one — and prints both results side by side.</p>
 *
 * <p>The unsynchronised run oversells, because
 * {@code if (sold < capacity) sold++} is a check-then-act pair: several threads
 * can pass the check before any of them performs the increment, and the
 * increment itself is a non-atomic read-modify-write, so updates are also simply
 * lost.</p>
 *
 * <p>Run it on its own with:</p>
 * <pre>java -cp bin RaceConditionDemo</pre>
 */
public class RaceConditionDemo {

    /** Seats available in the simulated ride. */
    private static final int CAPACITY = 500;

    /** Concurrent ticket counters in the demo. */
    private static final int THREAD_COUNT = 8;

    /** Booking attempts made by each thread. */
    private static final int ATTEMPTS_PER_THREAD = 200;

    private RaceConditionDemo() {
        // demo entry point only, never instantiated
    }

    /**
     * Console entry point.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.println(runDemo());
    }

    /**
     * Runs both workloads and formats a comparison report.
     *
     * @return a human-readable report, suitable for the console or a dialog
     */
    public static String runDemo() {
        SeatCounter unsafe = new SeatCounter(CAPACITY, false);
        SeatCounter safe = new SeatCounter(CAPACITY, true);

        int unsafeAccepted = hammer(unsafe);
        int safeAccepted = hammer(safe);

        StringBuilder sb = new StringBuilder();
        sb.append("RACE CONDITION DEMONSTRATION\n");
        sb.append("============================\n");
        sb.append(THREAD_COUNT).append(" threads x ").append(ATTEMPTS_PER_THREAD)
          .append(" booking attempts = ").append(THREAD_COUNT * ATTEMPTS_PER_THREAD)
          .append(" attempts against ").append(CAPACITY).append(" seats.\n\n");

        sb.append("WITHOUT synchronized\n");
        sb.append("  seats recorded as sold : ").append(unsafe.getSold()).append("\n");
        sb.append("  bookings confirmed     : ").append(unsafeAccepted).append("\n");
        sb.append("  oversold by            : ").append(Math.max(0, unsafeAccepted - CAPACITY)).append("\n");
        sb.append("  lost updates           : ").append(unsafeAccepted - unsafe.getSold()).append("\n\n");

        sb.append("WITH synchronized\n");
        sb.append("  seats recorded as sold : ").append(safe.getSold()).append("\n");
        sb.append("  bookings confirmed     : ").append(safeAccepted).append("\n");
        sb.append("  oversold by            : ").append(Math.max(0, safeAccepted - CAPACITY)).append("\n");
        sb.append("  lost updates           : ").append(safeAccepted - safe.getSold()).append("\n\n");

        boolean safeIsCorrect = safeAccepted == CAPACITY && safe.getSold() == CAPACITY;
        sb.append("Conclusion: the synchronized counter sold exactly ").append(CAPACITY)
          .append(safeIsCorrect ? " seats (correct)." : " seats.").append("\n");
        sb.append("The unsynchronised counter did not, because 'check capacity' and\n");
        sb.append("'increment' are two separate steps that other threads can interleave with.\n");
        sb.append("This is exactly why every mutator on Ride and ParkState is synchronized.\n");

        return sb.toString();
    }

    /**
     * Runs the fixed workload against one counter.
     *
     * @param counter the counter under test
     * @return how many booking attempts were confirmed
     */
    private static int hammer(SeatCounter counter) {
        List<Thread> threads = new ArrayList<>();
        List<BookingWorker> workers = new ArrayList<>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            BookingWorker worker = new BookingWorker(counter, ATTEMPTS_PER_THREAD);
            workers.add(worker);
            threads.add(new Thread(worker, "DemoCounter-" + (i + 1)));
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        int accepted = 0;
        for (BookingWorker w : workers) {
            accepted += w.getAccepted();
        }
        return accepted;
    }

    /**
     * A seat counter that is either safe or deliberately broken, so both runs
     * exercise byte-for-byte identical logic apart from the lock.
     */
    private static class SeatCounter {
        private final int capacity;
        private final boolean useLock;
        private int sold;

        /**
         * @param capacity how many seats exist
         * @param useLock  {@code true} to take the monitor around check-then-act
         */
        SeatCounter(int capacity, boolean useLock) {
            this.capacity = capacity;
            this.useLock = useLock;
            this.sold = 0;
        }

        /**
         * @return {@code true} when a seat was allocated
         */
        boolean book() {
            if (useLock) {
                synchronized (this) {
                    return bookUnguarded();
                }
            }
            return bookUnguarded();
        }

        /**
         * The check-then-act pair. Safe only when the caller holds the monitor.
         *
         * @return {@code true} when a seat was allocated
         */
        private boolean bookUnguarded() {
            if (sold < capacity) {
                int current = sold;
                Thread.yield(); // widens the window so the bug shows reliably
                sold = current + 1;
                return true;
            }
            return false;
        }

        /**
         * @return the recorded number of seats sold
         */
        int getSold() {
            if (useLock) {
                synchronized (this) {
                    return sold;
                }
            }
            return sold;
        }
    }

    /** One simulated ticket counter hammering the shared seat counter. */
    private static class BookingWorker implements Runnable {
        private final SeatCounter counter;
        private final int attempts;
        private int accepted;

        /**
         * @param counter  the shared counter
         * @param attempts how many bookings to attempt
         */
        BookingWorker(SeatCounter counter, int attempts) {
            this.counter = counter;
            this.attempts = attempts;
            this.accepted = 0;
        }

        @Override
        public void run() {
            for (int i = 0; i < attempts; i++) {
                if (counter.book()) {
                    accepted++;
                }
            }
        }

        /**
         * @return how many of this worker's attempts were confirmed
         */
        int getAccepted() {
            return accepted;
        }
    }
}
