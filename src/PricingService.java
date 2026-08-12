/**
 * Static facade over the {@link PricingStrategy} implementations.
 *
 * <p>The rest of the system stores a pricing <em>period</em> as an {@code int}
 * (so that {@link ParkState} stays trivially serializable) and asks this service
 * to resolve that int to the matching strategy object. Adding a period means
 * adding one class and one array entry here.</p>
 */
public final class PricingService {

    /** Period index for {@link StandardPricing}. */
    public static final int PERIOD_STANDARD = 0;

    /** Period index for {@link PeakHourPricing}. */
    public static final int PERIOD_PEAK = 1;

    /** Period index for {@link StudentDiscountPricing}. */
    public static final int PERIOD_STUDENT = 2;

    /** Period index for {@link PublicHolidayPricing}. */
    public static final int PERIOD_HOLIDAY = 3;

    private static final PricingStrategy[] STRATEGIES = {
        new StandardPricing(),
        new PeakHourPricing(),
        new StudentDiscountPricing(),
        new PublicHolidayPricing()
    };

    /** How many pricing periods the clock cycles through. */
    public static final int PERIOD_COUNT = STRATEGIES.length;

    private PricingService() {
        // utility class, never instantiated
    }

    /**
     * Resolves a period index to its strategy, falling back to
     * {@link StandardPricing} for out-of-range values.
     *
     * @param period one of the {@code PERIOD_*} constants
     * @return the strategy for that period, never {@code null}
     */
    public static PricingStrategy forPeriod(int period) {
        if (period < 0 || period >= STRATEGIES.length) {
            return STRATEGIES[PERIOD_STANDARD];
        }
        return STRATEGIES[period];
    }

    /**
     * Prices a ticket under the given period's strategy.
     *
     * @param ticketType one of the {@code Ticket.TYPE_*} constants
     * @param period     one of the {@code PERIOD_*} constants
     * @param groupSize  total number of people, only used for group tickets
     * @return the final price in dollars
     */
    public static double calculatePrice(int ticketType, int period, int groupSize) {
        return forPeriod(period).priceFor(ticketType, groupSize);
    }

    /**
     * @param period one of the {@code PERIOD_*} constants
     * @return that period's price multiplier
     */
    public static double getPeriodMultiplier(int period) {
        return forPeriod(period).getMultiplier();
    }

    /**
     * @param period one of the {@code PERIOD_*} constants
     * @return that period's display name
     */
    public static String getPeriodString(int period) {
        return forPeriod(period).getDisplayName();
    }
}
