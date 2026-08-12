/**
 * A maintenance technician — the third level of the
 * {@code Person -> Staff -> Technician} inheritance chain.
 *
 * <p>Certification level gates which rides a technician may work on; see
 * {@link ParkState#canTechnicianRepair(Technician, Ride)}.</p>
 */
public class Technician extends Staff {
    private static final long serialVersionUID = 2L;

    /** May repair climbing walls and Ferris wheels. */
    public static final int LEVEL_JUNIOR = 0;

    /** May additionally repair water slides and zip lines. */
    public static final int LEVEL_MID = 1;

    /** May repair anything, including roller coasters. */
    public static final int LEVEL_SENIOR = 2;

    private int certificationLevel;
    private boolean busy;
    private int repairsCompleted;
    private long totalBusyMs;
    private transient long busySince;

    /**
     * @param name               technician's name
     * @param age                technician's age
     * @param employeeId         staff identifier
     * @param certificationLevel one of the {@code LEVEL_*} constants
     */
    public Technician(String name, int age, String employeeId, int certificationLevel) {
        super(name, age, employeeId);
        setCertificationLevel(certificationLevel);
        this.busy = false;
        this.repairsCompleted = 0;
        this.totalBusyMs = 0L;
        this.busySince = 0L;
    }

    /** @return one of the {@code LEVEL_*} constants */
    public int getCertificationLevel() {
        return certificationLevel;
    }

    /**
     * @param level one of the {@code LEVEL_*} constants
     * @throws IllegalArgumentException when out of range
     */
    public void setCertificationLevel(int level) {
        if (level < LEVEL_JUNIOR || level > LEVEL_SENIOR) {
            throw new IllegalArgumentException("Invalid certification level");
        }
        this.certificationLevel = level;
    }

    /**
     * @return {@code true} when already assigned to a task
     */
    public synchronized boolean isBusy() {
        return busy;
    }

    /**
     * Marks the technician busy or idle, accumulating time on the clock.
     *
     * @param busy {@code true} when taking a job, {@code false} when finished
     */
    public synchronized void setBusy(boolean busy) {
        if (busy && !this.busy) {
            busySince = System.currentTimeMillis();
        } else if (!busy && this.busy) {
            if (busySince > 0L) {
                totalBusyMs += System.currentTimeMillis() - busySince;
            }
            busySince = 0L;
        }
        this.busy = busy;
    }

    /** Records one finished repair against this technician's tally. */
    public synchronized void recordRepairCompleted() {
        repairsCompleted++;
    }

    /** @return how many repairs this technician has finished */
    public synchronized int getRepairsCompleted() {
        return repairsCompleted;
    }

    /**
     * @return total milliseconds spent on jobs, including one in progress
     */
    public synchronized long getTotalBusyMs() {
        long open = (busy && busySince > 0L) ? System.currentTimeMillis() - busySince : 0L;
        return totalBusyMs + open;
    }

    /**
     * @return time on jobs formatted as {@code "2m 5s"}
     */
    public synchronized String getBusyTimeString() {
        long seconds = getTotalBusyMs() / 1000L;
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }

    /**
     * @return average seconds per completed repair, 0 when none finished
     */
    public synchronized double getAverageRepairSeconds() {
        if (repairsCompleted == 0) {
            return 0.0;
        }
        return (getTotalBusyMs() / 1000.0) / repairsCompleted;
    }

    /** @return a human-readable certification level */
    public String getCertificationLevelString() {
        switch (certificationLevel) {
            case LEVEL_JUNIOR: return "Junior";
            case LEVEL_MID: return "Mid-Level";
            case LEVEL_SENIOR: return "Senior";
            default: return "Unknown";
        }
    }

    @Override
    public String toString() {
        return getName() + " (" + getCertificationLevelString() + ")";
    }
}
