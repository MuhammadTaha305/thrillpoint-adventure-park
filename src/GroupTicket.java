import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A ticket covering the purchaser <em>plus</em> a list of named members.
 *
 * <p><strong>Group size counts the buyer.</strong> {@code members} holds only the
 * additional people entered in the GUI, so the party size is
 * {@code members.size() + 1}. Pricing and queue-capacity checks must both use
 * {@link #getGroupSize()} rather than the raw list size.</p>
 *
 * <p>Each {@link GroupMember} carries their own height, so the ride's safety
 * minimum is enforced per person rather than once for the buyer.</p>
 */
public class GroupTicket extends Ticket {
    private static final long serialVersionUID = 3L;

    private List<GroupMember> members;

    /**
     * @param ticketId    unique identifier
     * @param visitorName the purchaser, who is <em>not</em> in {@code members}
     * @param basePrice   price paid at time of sale
     * @param members     the additional members, at least one
     */
    public GroupTicket(String ticketId, String visitorName, double basePrice, List<GroupMember> members) {
        super(ticketId, visitorName, basePrice, TYPE_GROUP);
        setMembers(members);
    }

    /**
     * @return the additional members, excluding the purchaser; unmodifiable
     */
    public List<GroupMember> getMembers() {
        return Collections.unmodifiableList(members);
    }

    /**
     * @param members the additional members, excluding the purchaser
     * @throws IllegalArgumentException when null or empty
     */
    public void setMembers(List<GroupMember> members) {
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("Group must contain at least one member besides the buyer");
        }
        this.members = new ArrayList<>(members);
    }

    /**
     * @return just the member names, for display
     */
    public List<String> getMemberNames() {
        List<String> names = new ArrayList<>();
        for (GroupMember m : members) {
            names.add(m.getName());
        }
        return names;
    }

    /**
     * @return the total party size, <strong>including the purchaser</strong>
     */
    public int getGroupSize() {
        return members.size() + 1;
    }

    /**
     * @return the shortest member, used when reporting a height refusal
     */
    public GroupMember getShortestMember() {
        GroupMember shortest = members.get(0);
        for (GroupMember m : members) {
            if (m.getHeight() < shortest.getHeight()) {
                shortest = m;
            }
        }
        return shortest;
    }

    @Override
    public boolean isValidForRide(Ride ride) {
        return true;
    }
}
