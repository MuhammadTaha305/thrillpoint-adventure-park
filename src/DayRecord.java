import java.io.Serializable;

/**
 * An immutable snapshot of one completed operating day.
 *
 * <p>Written by {@link ParkState#closeDay(int)} when {@link ParkClock} closes the
 * park. Park totals are cumulative for the lifetime of the park, so each record
 * stores the <em>delta</em> since the previous close — which is what a
 * day-over-day trend needs.</p>
 */
public class DayRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int dayNumber;
    private final double revenue;
    private final int ridersServed;
    private final int ticketsSold;
    private final int repairsCompleted;

    /**
     * @param dayNumber        the operating day this covers, starting at 1
     * @param revenue          revenue taken during the day
     * @param ridersServed     riders carried during the day
     * @param ticketsSold      tickets sold during the day
     * @param repairsCompleted maintenance tasks closed during the day
     */
    public DayRecord(int dayNumber, double revenue, int ridersServed,
                     int ticketsSold, int repairsCompleted) {
        this.dayNumber = dayNumber;
        this.revenue = revenue;
        this.ridersServed = ridersServed;
        this.ticketsSold = ticketsSold;
        this.repairsCompleted = repairsCompleted;
    }

    /** @return the operating day this record covers */
    public int getDayNumber() {
        return dayNumber;
    }

    /** @return revenue taken during the day */
    public double getRevenue() {
        return revenue;
    }

    /** @return riders carried during the day */
    public int getRidersServed() {
        return ridersServed;
    }

    /** @return tickets sold during the day */
    public int getTicketsSold() {
        return ticketsSold;
    }

    /** @return maintenance tasks closed during the day */
    public int getRepairsCompleted() {
        return repairsCompleted;
    }

    /**
     * @return average revenue per rider, or 0 when nobody rode
     */
    public double getRevenuePerRider() {
        return ridersServed == 0 ? 0.0 : revenue / ridersServed;
    }

    @Override
    public String toString() {
        return "Day " + dayNumber + ": $" + String.format("%.2f", revenue)
               + ", " + ridersServed + " riders, " + ticketsSold + " tickets, "
               + repairsCompleted + " repairs";
    }
}
