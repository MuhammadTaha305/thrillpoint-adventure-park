/**
 * Busy-period pricing: a 25% surcharge on every ticket type.
 */
public class PeakHourPricing implements PricingStrategy {

    @Override
    public double getMultiplier() {
        return 1.25;
    }

    @Override
    public String getDisplayName() {
        return "Peak Hour";
    }
}
