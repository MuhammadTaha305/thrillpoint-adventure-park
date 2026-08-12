/**
 * Global pause switch and speed dial for every simulation thread.
 *
 * <p>Singleton, because all fourteen threads and the GUI have to agree on one
 * setting and threading it through every constructor would be noise.</p>
 *
 * <h2>How pausing works without wait/notify</h2>
 * <p>{@code wait()}/{@code notify()} are outside the course scope, so a paused
 * thread is not parked — it polls {@link #isPaused()} in short slices inside
 * {@link #sleepScaled(long)}. The cost is up to {@value #POLL_SLICE_MS}ms of
 * latency when resuming, which is invisible at simulation timescales.</p>
 *
 * <p>Because every thread's wait is chopped into slices, changing the speed or
 * hitting pause takes effect within one slice rather than at the end of a
 * multi-second sleep.</p>
 */
public final class SimulationControl {

    /** Longest single sleep a thread performs before re-checking the controls. */
    public static final long POLL_SLICE_MS = 100L;

    /** Slowest setting offered. */
    public static final double MIN_SPEED = 0.25;

    /** Fastest setting offered. */
    public static final double MAX_SPEED = 8.0;

    private static SimulationControl instance;

    private boolean paused;
    private double speedFactor;

    private SimulationControl() {
        this.paused = false;
        this.speedFactor = 1.0;
    }

    /**
     * @return the singleton instance, created on first use
     */
    public static synchronized SimulationControl getInstance() {
        if (instance == null) {
            instance = new SimulationControl();
        }
        return instance;
    }

    /** @return {@code true} while the simulation is frozen */
    public synchronized boolean isPaused() {
        return paused;
    }

    /** @param paused {@code true} to freeze every simulation thread */
    public synchronized void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * Flips the pause state.
     *
     * @return the new state, {@code true} when now paused
     */
    public synchronized boolean togglePaused() {
        paused = !paused;
        return paused;
    }

    /**
     * @return the current speed multiplier; 2.0 means everything happens twice
     *         as fast
     */
    public synchronized double getSpeedFactor() {
        return speedFactor;
    }

    /**
     * @param speedFactor the new multiplier, clamped to
     *                    {@value #MIN_SPEED}..{@value #MAX_SPEED}
     */
    public synchronized void setSpeedFactor(double speedFactor) {
        if (speedFactor < MIN_SPEED) {
            this.speedFactor = MIN_SPEED;
        } else if (speedFactor > MAX_SPEED) {
            this.speedFactor = MAX_SPEED;
        } else {
            this.speedFactor = speedFactor;
        }
    }

    /**
     * @return a short label for the GUI, e.g. {@code "PAUSED"} or {@code "2.0x"}
     */
    public synchronized String getStatusLabel() {
        return paused ? "PAUSED" : String.format("%.2gx", speedFactor);
    }

    /**
     * Sleeps for a simulated duration, honouring pause and speed.
     *
     * <p>Never hold a lock across this call — it can block indefinitely while the
     * simulation is paused.</p>
     *
     * @param baseMillis the duration at normal speed
     * @return {@code true} when the full duration elapsed, {@code false} when the
     *         thread was interrupted (the interrupt flag is restored)
     */
    public boolean sleepScaled(long baseMillis) {
        long remaining = Math.max(0L, (long) (baseMillis / getSpeedFactor()));

        while (remaining > 0L) {
            if (isPaused()) {
                if (!nap(POLL_SLICE_MS)) {
                    return false;
                }
                continue;
            }
            long slice = Math.min(POLL_SLICE_MS, remaining);
            if (!nap(slice)) {
                return false;
            }
            remaining -= slice;
        }
        return true;
    }

    /**
     * Blocks while the simulation is paused, without consuming simulated time.
     *
     * @return {@code true} when the simulation is running, {@code false} when the
     *         thread was interrupted
     */
    public boolean awaitResume() {
        while (isPaused()) {
            if (!nap(POLL_SLICE_MS)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param millis how long to sleep
     * @return {@code true} when the sleep completed
     */
    private boolean nap(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
