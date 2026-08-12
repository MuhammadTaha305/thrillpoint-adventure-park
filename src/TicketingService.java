import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Use-case logic for selling and cancelling bookings.
 *
 * <h2>Failure reporting</h2>
 * <p>Every refusal is a thrown {@link ParkException} subclass, not a magic
 * return string. Callers get the reason as a type they can react to, the cause
 * chain survives, and no caller can forget to check.</p>
 *
 * <h2>Locking</h2>
 * <p>Follows the park-wide order: the ride monitor is taken first and every
 * {@link ParkState} call happens inside it. The ride lock is held for the whole
 * validate-price-enqueue sequence so that a ride cannot go under maintenance
 * between the status check and the enqueue, but it is never held across a sleep.</p>
 */
public class TicketingService {

    private TicketingService() {
        // static use-case class, never instantiated
    }

    /**
     * Sells a ticket and puts the whole party into the ride's queue.
     *
     * @param parkState    the park; must not be {@code null}
     * @param name         the purchasing visitor's name
     * @param age          the purchasing visitor's age
     * @param height       the purchasing visitor's height in metres
     * @param ticketType   one of the {@code Ticket.TYPE_*} constants
     * @param ride         the ride to queue for
     * @param groupMembers additional members for a group ticket, each with their
     *                     own height, otherwise {@code null}
     * @return the issued ticket
     * @throws HeightRestrictionException     when the visitor is too short
     * @throws MaintenanceInProgressException when the ride is being repaired
     * @throws RideClosedException            when the ride is closed
     * @throws CapacityExceededException      when the queue cannot fit the party
     * @throws InvalidTicketException         when the request itself is malformed
     */
    public static Ticket bookVisitor(ParkState parkState, String name, int age, double height,
                                     int ticketType, Ride ride, List<GroupMember> groupMembers)
            throws ParkException {

        Objects.requireNonNull(parkState, "parkState must not be null");
        if (ride == null) {
            throw new InvalidTicketException("Booking failed: no ride selected.");
        }

        // A group is the buyer PLUS everyone named in the GUI. Counting only the
        // named members undercharged every group ticket by exactly one person.
        int partySize = 1;
        List<GroupMember> members = null;
        if (ticketType == Ticket.TYPE_GROUP) {
            if (groupMembers == null || groupMembers.isEmpty()) {
                throw new InvalidTicketException(
                    "Booking failed: a group ticket needs at least one member besides the buyer.");
            }
            members = new ArrayList<>(groupMembers);
            partySize = members.size() + 1;
        }

        Ticket ticket;
        double price;

        synchronized (ride) {
            if (!ride.admitsHeight(height)) {
                throw new HeightRestrictionException(ride.getName(), height, ride.getMinHeight());
            }

            // Every member is checked individually. Inheriting the buyer's height
            // used to let a child ride anything an adult booked.
            if (members != null) {
                for (GroupMember member : members) {
                    if (!ride.admitsHeight(member.getHeight())) {
                        throw new HeightRestrictionException(
                            ride.getName() + " (group member " + member.getName() + ")",
                            member.getHeight(), ride.getMinHeight());
                    }
                }
            }

            int status = ride.getStatus();
            if (status == Ride.STATUS_UNDER_MAINTENANCE) {
                throw new MaintenanceInProgressException(ride.getName());
            }
            if (status == Ride.STATUS_CLOSED) {
                throw new RideClosedException(ride.getName());
            }

            int free = ride.getFreeQueueSlots();
            if (free < partySize) {
                throw new CapacityExceededException(ride.getName(), partySize, free);
            }

            int currentPeriod = parkState.getCurrentPeriod();
            price = PricingService.calculatePrice(ticketType, currentPeriod, partySize);

            // Minted under the ParkState lock, never derived from a list size.
            String ticketId = parkState.nextTicketId();
            ticket = createTicket(ticketId, name, price, ticketType, ride, members);

            List<Visitor> party = buildParty(name, age, height, members, ticket);
            for (Visitor v : party) {
                parkState.registerVisitor(v);
                ride.enqueueVisitor(v);
            }

            parkState.addSoldTicket(ticket);
            parkState.addRevenue(price);
            ride.addRevenue(price);
        }

        AuditLogger.getInstance().log("TICKETING",
            "Ticket " + ticket.getTicketId() + " (" + ticket.getTicketTypeString() + ") sold to "
            + name + " for $" + String.format("%.2f", price) + " covering " + partySize
            + (partySize == 1 ? " person" : " people") + ". Queued on " + ride.getName() + ".");

        return ticket;
    }

    /**
     * Cancels one visitor's place in a queue and refunds the ticket once the last
     * member of the party has left (FR-05).
     *
     * @param parkState   the park
     * @param ride        the ride to remove the visitor from
     * @param visitorName the visitor to remove, matched case-insensitively
     * @return {@code true} when a visitor was found and removed
     * @throws InvalidTicketException when no ride was supplied
     */
    public static boolean cancelBooking(ParkState parkState, Ride ride, String visitorName)
            throws ParkException {

        Objects.requireNonNull(parkState, "parkState must not be null");
        if (ride == null) {
            throw new InvalidTicketException("Cancellation failed: no ride selected.");
        }

        String logMessage;

        synchronized (ride) {
            Visitor removed = ride.cancelQueuedVisitor(visitorName);
            if (removed == null) {
                return false;
            }
            parkState.unregisterVisitor(removed);

            Ticket ticket = removed.getTicket();
            boolean refunded = false;
            if (ticket != null && !partyStillQueued(ride, ticket)) {
                double refund = ticket.getCurrentPrice();
                parkState.removeSoldTicket(ticket);
                parkState.addRevenue(-refund);
                ride.addRevenue(-refund);
                refunded = true;
            }

            logMessage = "Booking cancelled for " + removed.getName() + " on " + ride.getName()
                         + (refunded
                            ? ". Ticket " + ticket.getTicketId() + " refunded $"
                              + String.format("%.2f", ticket.getCurrentPrice()) + "."
                            : ". Other members of the party are still queued, no refund issued.");
        }

        AuditLogger.getInstance().log("TICKETING", logMessage);
        return true;
    }

    /**
     * Gate check applied at the moment a visitor is loaded onto a ride.
     *
     * <p>This is where a Day Pass stops working after the park closes (FR-11) and
     * where a used or wrong-ride single-ride ticket is rejected (P3).</p>
     *
     * @param visitor the visitor about to board
     * @param ride    the ride being boarded
     * @throws TicketExpiredException when the park has closed on this ticket
     * @throws InvalidTicketException when the ticket is missing or not valid here
     */
    public static void verifyBoarding(Visitor visitor, Ride ride) throws ParkException {
        Ticket ticket = visitor.getTicket();
        if (ticket == null) {
            throw new InvalidTicketException(visitor.getName() + " is holding no ticket.");
        }
        if (ticket.isExpired()) {
            throw new TicketExpiredException(ticket.getTicketId());
        }
        if (!ticket.isValidForRide(ride)) {
            throw new InvalidTicketException(
                "Ticket " + ticket.getTicketId() + " is not valid for " + ride.getName() + ".");
        }
    }

    /**
     * @param ride   the ride whose queue to scan; caller holds its lock
     * @param ticket the ticket to look for
     * @return {@code true} when somebody on {@code ticket} is still waiting
     */
    private static boolean partyStillQueued(Ride ride, Ticket ticket) {
        for (Visitor v : ride.getQueue()) {
            if (v.getTicket() == ticket) {
                return true;
            }
        }
        return false;
    }

    /**
     * Polymorphic factory for the four ticket types.
     *
     * @param ticketId   the freshly minted identifier
     * @param name       the purchaser
     * @param price      the priced amount
     * @param ticketType one of the {@code Ticket.TYPE_*} constants
     * @param ride       the target ride, needed by single-ride tickets
     * @param members    group member names, or {@code null}
     * @return the new ticket
     * @throws InvalidTicketException when {@code ticketType} is unrecognised
     */
    private static Ticket createTicket(String ticketId, String name, double price, int ticketType,
                                       Ride ride, List<GroupMember> members) throws InvalidTicketException {
        switch (ticketType) {
            case Ticket.TYPE_SINGLE:
                return new SingleRideTicket(ticketId, name, price, ride.getName());
            case Ticket.TYPE_DAY_PASS:
                return new DayPassTicket(ticketId, name, price);
            case Ticket.TYPE_VIP:
                return new VIPFastPassTicket(ticketId, name, price);
            case Ticket.TYPE_GROUP:
                return new GroupTicket(ticketId, name, price, members);
            default:
                throw new InvalidTicketException("Booking failed: unknown ticket type " + ticketType + ".");
        }
    }

    /**
     * Builds the buyer plus every named group member.
     *
     * <p>Members keep their own height. Age still comes from the buyer, since the
     * form does not collect a per-member age and nothing in the system gates on
     * it.</p>
     *
     * @param name    the buyer's name
     * @param age     the buyer's age
     * @param height  the buyer's height in metres
     * @param members additional members, or {@code null}
     * @param ticket  the shared ticket
     * @return the whole party, buyer first
     */
    private static List<Visitor> buildParty(String name, int age, double height,
                                            List<GroupMember> members, Ticket ticket) {
        List<Visitor> party = new ArrayList<>();
        Visitor buyer = new Visitor(name, age, height);
        buyer.setTicket(ticket);
        party.add(buyer);

        if (members != null) {
            for (GroupMember member : members) {
                Visitor visitor = new Visitor(member.getName(), age, member.getHeight());
                visitor.setTicket(ticket);
                party.add(visitor);
            }
        }
        return party;
    }

    /**
     * Puts a visitor who already holds a valid ticket back into a ride queue
     * without selling them anything.
     *
     * <p>This is what makes a Day Pass and a VIP Fast Pass mean what they say.
     * Until this existed, every booking minted a brand-new ticket, so "unlimited
     * rides" was never actually exercised — a day pass holder had to buy another
     * day pass to ride again.</p>
     *
     * @param parkState the park
     * @param visitor   an already-registered visitor holding a ticket
     * @param ride      the ride to queue for
     * @throws TicketExpiredException         when the park has closed on the ticket
     * @throws InvalidTicketException         when the ticket does not admit them here
     * @throws HeightRestrictionException     when the visitor is too short
     * @throws MaintenanceInProgressException when the ride is being repaired
     * @throws RideClosedException            when the ride is closed
     * @throws CapacityExceededException      when the queue is full
     */
    public static void boardExistingVisitor(ParkState parkState, Visitor visitor, Ride ride)
            throws ParkException {

        Objects.requireNonNull(parkState, "parkState must not be null");
        Objects.requireNonNull(visitor, "visitor must not be null");
        if (ride == null) {
            throw new InvalidTicketException("Boarding failed: no ride selected.");
        }

        synchronized (ride) {
            verifyBoarding(visitor, ride);

            if (!ride.admitsHeight(visitor.getHeight())) {
                throw new HeightRestrictionException(
                    ride.getName(), visitor.getHeight(), ride.getMinHeight());
            }

            int status = ride.getStatus();
            if (status == Ride.STATUS_UNDER_MAINTENANCE) {
                throw new MaintenanceInProgressException(ride.getName());
            }
            if (status == Ride.STATUS_CLOSED) {
                throw new RideClosedException(ride.getName());
            }

            if (isAlreadyQueued(ride, visitor)) {
                throw new InvalidTicketException(
                    visitor.getName() + " is already waiting for " + ride.getName() + ".");
            }

            int free = ride.getFreeQueueSlots();
            if (free < 1) {
                throw new CapacityExceededException(ride.getName(), 1, free);
            }

            ride.enqueueVisitor(visitor);
        }

        AuditLogger.getInstance().log("TICKETING",
            visitor.getName() + " re-boarded " + ride.getName() + " on existing ticket "
            + visitor.getTicket().getTicketId() + " (" + visitor.getTicket().getTicketTypeString()
            + "). No charge.");
    }

    /**
     * @param ride    the ride whose queue to scan; caller holds its lock
     * @param visitor the visitor to look for
     * @return {@code true} when this exact visitor is already waiting
     */
    private static boolean isAlreadyQueued(Ride ride, Visitor visitor) {
        for (Visitor v : ride.getQueue()) {
            if (v == visitor) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds which ride a visitor is currently waiting for.
     *
     * @param parkState the park
     * @param visitor   the visitor to locate
     * @return the ride they are queued on, or {@code null} when they are not
     *         waiting anywhere
     */
    public static Ride findQueuedRide(ParkState parkState, Visitor visitor) {
        for (Ride ride : parkState.getRides()) {
            for (Visitor v : ride.getQueue()) {
                if (v == visitor) {
                    return ride;
                }
            }
        }
        return null;
    }
}
