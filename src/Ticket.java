import java.io.Serializable;

/**
 * Abstract base of the four ticket types the park sells.
 *
 * <p>This hierarchy is the project's runtime-polymorphism showcase: the ticketing
 * and ride-loading code calls {@link #canBoard(Ride)} without ever asking what
 * kind of ticket it is holding.</p>
 *
 * @see SingleRideTicket
 * @see DayPassTicket
 * @see VIPFastPassTicket
 * @see GroupTicket
 */
public abstract class Ticket implements Serializable, Identifiable {
    private static final long serialVersionUID = 2L;

    /** Ticket type constant: valid for one ride, once. */
    public static final int TYPE_SINGLE = 0;

    /** Ticket type constant: valid for every ride until the park closes. */
    public static final int TYPE_DAY_PASS = 1;

    /** Ticket type constant: valid for every ride, and jumps the queue. */
    public static final int TYPE_VIP = 2;

    /** Ticket type constant: covers a buyer plus a list of named members. */
    public static final int TYPE_GROUP = 3;

    private String ticketId;
    private String visitorName;
    private double basePrice;
    private double currentPrice;
    private int ticketType;
    private boolean expired;

    /**
     * @param ticketId    unique identifier, e.g. {@code "TCK-12"}
     * @param visitorName name of the purchasing visitor
     * @param basePrice   price paid at time of sale
     * @param ticketType  one of the {@code TYPE_*} constants
     */
    public Ticket(String ticketId, String visitorName, double basePrice, int ticketType) {
        setTicketId(ticketId);
        setVisitorName(visitorName);
        setBasePrice(basePrice);
        this.currentPrice = basePrice;
        this.ticketType = ticketType;
        this.expired = false;
    }

    /** @return the unique ticket identifier */
    public String getTicketId() {
        return ticketId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Satisfies {@link Identifiable} by delegating to {@link #getTicketId()}.</p>
     */
    @Override
    public String getId() {
        return ticketId;
    }

    /**
     * @param ticketId the unique identifier
     * @throws IllegalArgumentException when blank
     */
    public void setTicketId(String ticketId) {
        if (ticketId == null || ticketId.trim().isEmpty()) {
            throw new IllegalArgumentException("Ticket ID cannot be empty");
        }
        this.ticketId = ticketId;
    }

    /** @return the name of the purchasing visitor */
    public String getVisitorName() {
        return visitorName;
    }

    /**
     * @param visitorName the purchaser's name
     * @throws IllegalArgumentException when blank
     */
    public void setVisitorName(String visitorName) {
        if (visitorName == null || visitorName.trim().isEmpty()) {
            throw new IllegalArgumentException("Visitor name cannot be empty");
        }
        this.visitorName = visitorName;
    }

    /** @return the undiscounted price */
    public double getBasePrice() {
        return basePrice;
    }

    /**
     * @param basePrice the undiscounted price
     * @throws IllegalArgumentException when negative
     */
    public void setBasePrice(double basePrice) {
        if (basePrice < 0.0) {
            throw new IllegalArgumentException("Base price cannot be negative");
        }
        this.basePrice = basePrice;
    }

    /** @return the price actually charged, after the pricing strategy */
    public double getCurrentPrice() {
        return currentPrice;
    }

    /**
     * @param price the price actually charged
     * @throws IllegalArgumentException when negative
     */
    public void setCurrentPrice(double price) {
        if (price < 0.0) {
            throw new IllegalArgumentException("Current price cannot be negative");
        }
        this.currentPrice = price;
    }

    /** @return one of the {@code TYPE_*} constants */
    public int getTicketType() {
        return ticketType;
    }

    /** @return {@code true} once the park has closed on this ticket */
    public synchronized boolean isExpired() {
        return expired;
    }

    /**
     * Marks this ticket as no longer usable. Called by {@link ParkClock} at
     * closing time (FR-11).
     */
    public synchronized void expire() {
        this.expired = true;
    }

    /** @return a human-readable name for the ticket type */
    public String getTicketTypeString() {
        switch (ticketType) {
            case TYPE_SINGLE: return "Single Ride";
            case TYPE_DAY_PASS: return "Day Pass";
            case TYPE_VIP: return "VIP Fast Pass";
            case TYPE_GROUP: return "Group Ticket";
            default: return "Unknown";
        }
    }

    /**
     * Type-specific access rule, overridden by every concrete ticket.
     *
     * <p>Expiry is <em>not</em> checked here — it applies to every ticket type
     * identically, so it lives once in
     * {@link TicketingService#verifyBoarding(Visitor, Ride)} rather than being
     * repeated (and forgotten) in each subclass.</p>
     *
     * @param ride the ride the holder wants to board
     * @return {@code true} when this ticket type grants access to {@code ride}
     */
    public abstract boolean isValidForRide(Ride ride);

    @Override
    public String toString() {
        return ticketId + " (" + getTicketTypeString() + ") - " + visitorName
               + " $" + String.format("%.2f", currentPrice);
    }
}
