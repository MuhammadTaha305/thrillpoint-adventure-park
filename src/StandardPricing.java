/**
 * Default pricing: base prices with no adjustment.
 */
public class StandardPricing implements PricingStrategy {

    @Override
    public double getMultiplier() {
        return 1.0;
    }

    @Override
    public String getDisplayName() {
        return "Standard";
    }
}
