/**
 * Student window pricing: a 20% discount on every ticket type.
 */
public class StudentDiscountPricing implements PricingStrategy {

    @Override
    public double getMultiplier() {
        return 0.80;
    }

    @Override
    public String getDisplayName() {
        return "Student Discount";
    }
}
