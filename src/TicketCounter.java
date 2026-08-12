import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Simulates one physical ticket window (FR-09 / P9).
 *
 * <p>Three of these run concurrently and deliberately contend for the same rides,
 * which is what makes the {@code synchronized} seat and queue handling
 * observable. Every refusal arrives as a typed {@link ParkException}, so the
 * counter logs a precise reason rather than parsing a string.</p>
 */
public class TicketCounter implements Runnable {

    private static final String[] NAMES = {
        "Alice", "Bob", "Charlie", "David", "Emma", "Frank", "Grace", "Henry", "Ivy",
        "Jack", "Kate", "Liam", "Mila", "Noah", "Olivia", "Peter", "Quinn", "Ryan",
        "Sophia", "Thomas", "Ursula", "Victor", "Wendy", "Xavier", "Yara", "Zach"
    };

    private final String counterId;
    private final ParkState parkState;
    private final Random random;
    private boolean running;

    /**
     * @param counterId display name, e.g. {@code "Counter-1"}
     * @param parkState the park to sell tickets into
     */
    public TicketCounter(String counterId, ParkState parkState) {
        this.counterId = counterId;
        this.parkState = parkState;
        this.random = new Random();
        this.running = true;
    }

    /** Asks this thread to stop after its current transaction. */
    public synchronized void stopCounter() {
        this.running = false;
    }

    /** @return {@code true} while this counter should keep selling */
    private synchronized boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        AuditLogger.getInstance().log("SYSTEM", "Ticket Counter " + counterId + " thread started.");

        while (isRunning()) {
            if (!SimulationControl.getInstance().sleepScaled(3000 + random.nextInt(4000))) {
                break;
            }

            List<Ride> rides = parkState.getRides();
            if (rides.isEmpty()) {
                continue;
            }

            Ride ride = rides.get(random.nextInt(rides.size()));
            String name = NAMES[random.nextInt(NAMES.length)] + "-" + random.nextInt(100);
            int age = 5 + random.nextInt(60);
            double height = 0.9 + random.nextDouble();
            int ticketType = random.nextInt(4);

            List<GroupMember> groupMembers = null;
            if (ticketType == Ticket.TYPE_GROUP) {
                groupMembers = new ArrayList<>();
                int partySize = 2 + random.nextInt(4);
                for (int i = 0; i < partySize - 1; i++) {
                    // Group members are drawn from a taller band than walk-ups:
                    // with four independent draws from the full 0.9-1.9m range
                    // almost every group would fail a coaster's height check and
                    // the log would be nothing but refusals.
                    groupMembers.add(new GroupMember(
                        NAMES[random.nextInt(NAMES.length)] + "-" + random.nextInt(100),
                        1.25 + (0.65 * random.nextDouble())));
                }
            }

            try {
                TicketingService.bookVisitor(parkState, name, age, height, ticketType, ride, groupMembers);
            } catch (HeightRestrictionException e) {
                logRefusal(name, ride, "height restriction - " + e.getMessage());
            } catch (MaintenanceInProgressException | RideClosedException e) {
                logRefusal(name, ride, "ride unavailable - " + e.getMessage());
            } catch (CapacityExceededException e) {
                logRefusal(name, ride, "queue full - needed " + e.getRequested()
                                       + " slots, " + e.getAvailable() + " free");
            } catch (ParkException e) {
                logRefusal(name, ride, e.getMessage());
            }
        }

        AuditLogger.getInstance().log("SYSTEM", "Ticket Counter " + counterId + " thread stopped.");
    }

    /**
     * @param visitorName the visitor who was turned away
     * @param ride        the ride they wanted
     * @param reason      why the sale was refused
     */
    private void logRefusal(String visitorName, Ride ride, String reason) {
        AuditLogger.getInstance().log("TICKETING",
            "Counter " + counterId + " visitor " + visitorName + " booking rejected for "
            + ride.getName() + ". Reason: " + reason);
    }
}
