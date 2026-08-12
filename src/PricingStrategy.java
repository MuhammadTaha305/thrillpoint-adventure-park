/**
 * Strategy pattern: one interchangeable pricing rule for the park.
 *
 * <p>This is the project's Open/Closed Principle evidence. Adding a new pricing
 * period (say a "Rainy Day" discount) means adding one new class that implements
 * this interface and one entry in {@link PricingService} — no existing class is
 * edited.</p>
 *
 * <p>Demonstrates implicit {@code public static final} interface constants,
 * {@code default} methods and a {@code static} interface method.</p>
 */
public interface PricingStrategy {

    /** Undiscounted price of a single-ride ticket. */
    double BASE_SINGLE = 15.0;

    /** Undiscounted price of a day pass. */
    double BASE_DAY_PASS = 40.0;

    /** Undiscounted price of a VIP fast pass. */
    double BASE_VIP = 75.0;

    /** Undiscounted price of a group ticket, per person. */
    double BASE_GROUP_PER_PERSON = 25.0;

    /**
     * @return the factor this strategy applies to the base price
     */
    double getMultiplier();

    /**
     * @return the name shown in the GUI, e.g. {@code "Peak Hour"}
     */
    String getDisplayName();

    /**
     * Looks up the undiscounted price for a ticket type.
     *
     * @param ticketType one of the {@code Ticket.TYPE_*} constants
     * @param groupSize  total number of people, only used for group tickets
     * @return the base price before this strategy's multiplier is applied
     */
    default double basePriceFor(int ticketType, int groupSize) {
        switch (ticketType) {
            case Ticket.TYPE_SINGLE:
                return BASE_SINGLE;
            case Ticket.TYPE_DAY_PASS:
                return BASE_DAY_PASS;
            case Ticket.TYPE_VIP:
                return BASE_VIP;
            case Ticket.TYPE_GROUP:
                return BASE_GROUP_PER_PERSON * (groupSize > 0 ? groupSize : 1);
            default:
                return 0.0;
        }
    }

    /**
     * Prices a ticket under this strategy.
     *
     * @param ticketType one of the {@code Ticket.TYPE_*} constants
     * @param groupSize  total number of people, only used for group tickets
     * @return the final price, rounded to two decimal places
     */
    default double priceFor(int ticketType, int groupSize) {
        return roundToCents(basePriceFor(ticketType, groupSize) * getMultiplier());
    }

    /**
     * Rounds a money amount to two decimal places.
     *
     * @param amount the raw amount
     * @return {@code amount} rounded to the nearest cent
     */
    static double roundToCents(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }
}
