/**
 * A paying guest.
 *
 * <p>Height is safety-relevant: {@link Ride#getMinHeight()} is checked against it
 * before any booking is accepted (P6 / FR-04).</p>
 */
public class Visitor extends Person {
    private static final long serialVersionUID = 2L;

    private double height;
    private Ticket ticket;

    /**
     * @param name   the visitor's name
     * @param age    the visitor's age in years
     * @param height the visitor's height in metres
     * @throws IllegalArgumentException when the height is not positive
     */
    public Visitor(String name, int age, double height) {
        super(name, age);
        setHeight(height);
    }

    /** @return the visitor's height in metres */
    public double getHeight() {
        return height;
    }

    /**
     * @param height the visitor's height in metres
     * @throws IllegalArgumentException when not positive
     */
    public void setHeight(double height) {
        if (height <= 0.0) {
            throw new IllegalArgumentException("Height must be positive");
        }
        this.height = height;
    }

    /**
     * @return the ticket this visitor is travelling on; group members share one
     *         ticket instance with their buyer
     */
    public Ticket getTicket() {
        return ticket;
    }

    /** @param ticket the ticket this visitor is travelling on */
    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    @Override
    public String toString() {
        return getName() + " (" + getAge() + ", " + String.format("%.2fm", height) + ")";
    }
}
