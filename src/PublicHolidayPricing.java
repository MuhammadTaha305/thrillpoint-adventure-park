/**
 * Public-holiday pricing: a 10% surcharge on every ticket type.
 */
public class PublicHolidayPricing implements PricingStrategy {

    @Override
    public double getMultiplier() {
        return 1.10;
    }

    @Override
    public String getDisplayName() {
        return "Holiday Rates";
    }
}
