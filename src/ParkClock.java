/**
 * The simulated park clock.
 *
 * <p>Drives two time-based rules: the pricing period rotates
 * ({@link PricingService}), and the park closes for the day, which expires every
 * outstanding ticket (FR-11).</p>
 *
 * <p>One real second is one tick and five simulated minutes.</p>
 */
public class ParkClock implements Runnable {

    /** Simulated minutes added per tick. */
    public static final int MINUTES_PER_TICK = 5;

    /** Ticks between pricing-period rotations. */
    private static final int TICKS_PER_PERIOD = 30;

    /** Ticks in one simulated operating day, after which the park closes. */
    private static final int TICKS_PER_PARK_DAY = 120;

    private final ParkState parkState;
    private boolean running;
    private long virtualMinutes;
    private int dayNumber;

    /**
     * @param parkState the park whose period and tickets this clock drives
     */
    public ParkClock(ParkState parkState) {
        this.parkState = parkState;
        this.running = true;
        this.virtualMinutes = 0;
        this.dayNumber = 1;
    }

    /** Asks this thread to stop at the next tick. */
    public synchronized void stopClock() {
        this.running = false;
    }

    /** @return {@code true} while the clock should keep ticking */
    private synchronized boolean isRunning() {
        return running;
    }

    /** @return simulated minutes elapsed since the park opened */
    public synchronized long getVirtualTime() {
        return virtualMinutes;
    }

    /** @return the current simulated operating day, starting at 1 */
    public synchronized int getDayNumber() {
        return dayNumber;
    }

    /**
     * @return the simulated time of day as {@code "HH:mm"}
     */
    public synchronized String getTimeOfDayString() {
        long minutesIntoDay = virtualMinutes % (24L * 60L);
        long hours = minutesIntoDay / 60L;
        long minutes = minutesIntoDay % 60L;
        return String.format("%02d:%02d", hours, minutes);
    }

    /**
     * Closes the park for the day: expires every outstanding ticket so that a Day
     * Pass cannot be reused tomorrow (FR-11).
     *
     * @return how many tickets were expired
     */
    public int closeParkForTheDay() {
        int closingDay;
        synchronized (this) {
            closingDay = dayNumber;
        }

        // File the day's figures before expiring anything, so the record covers
        // the day that just ended.
        DayRecord record = parkState.closeDay(closingDay);
        int expired = parkState.expireAllTickets();

        synchronized (this) {
            dayNumber++;
        }

        AuditLogger.getInstance().log("CLOCK",
            "Park closed for day " + closingDay + ". Takings $"
            + String.format("%.2f", record.getRevenue()) + " from " + record.getTicketsSold()
            + " ticket(s), " + record.getRidersServed() + " riders carried, "
            + record.getRepairsCompleted() + " repair(s) done. " + expired
            + " ticket(s) expired. Now starting day " + getDayNumber() + ".");
        return expired;
    }

    @Override
    public void run() {
        AuditLogger.getInstance().log("SYSTEM", "Park clock thread started.");

        int periodCycle = 0;
        int dayCycle = 0;

        while (isRunning()) {
            if (!SimulationControl.getInstance().sleepScaled(1000L)) {
                break;
            }

            synchronized (this) {
                virtualMinutes += MINUTES_PER_TICK;
            }

            periodCycle++;
            if (periodCycle >= TICKS_PER_PERIOD) {
                periodCycle = 0;
                int nextPeriod = (parkState.getCurrentPeriod() + 1) % PricingService.PERIOD_COUNT;
                parkState.setCurrentPeriod(nextPeriod);
                AuditLogger.getInstance().log("CLOCK",
                    "Pricing Period changed to: " + PricingService.getPeriodString(nextPeriod)
                    + " (Multiplier: " + PricingService.getPeriodMultiplier(nextPeriod) + "x)");
            }

            dayCycle++;
            if (dayCycle >= TICKS_PER_PARK_DAY) {
                dayCycle = 0;
                closeParkForTheDay();
            }
        }

        AuditLogger.getInstance().log("SYSTEM", "Park clock thread stopped.");
    }
}
