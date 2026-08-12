import java.io.Serializable;

/**
 * One named person on a group ticket, other than the buyer.
 *
 * <p>Carries their own height so the ride's safety minimum is checked per person.
 * Previously every member silently inherited the buyer's height, which meant a
 * short child in an adult's group bypassed the check entirely.</p>
 */
public class GroupMember implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Height assumed when a member is entered without one. */
    public static final double DEFAULT_HEIGHT = 1.60;

    private String name;
    private double height;

    /**
     * @param name   the member's name
     * @param height the member's height in metres
     * @throws IllegalArgumentException when the name is blank or the height is
     *                                  not positive
     */
    public GroupMember(String name, double height) {
        setName(name);
        setHeight(height);
    }

    /**
     * Parses one entry of the group members field.
     *
     * <p>Accepts {@code "Name"} or {@code "Name:1.42"}. Without a height the
     * member is assumed to be {@value #DEFAULT_HEIGHT}m.</p>
     *
     * @param entry the raw text for one member
     * @return the parsed member, or {@code null} when {@code entry} is blank
     * @throws IllegalArgumentException when the height part is not a number
     */
    public static GroupMember parse(String entry) {
        if (entry == null || entry.trim().isEmpty()) {
            return null;
        }
        String text = entry.trim();
        int colon = text.lastIndexOf(':');
        if (colon < 0) {
            return new GroupMember(text, DEFAULT_HEIGHT);
        }

        String namePart = text.substring(0, colon).trim();
        String heightPart = text.substring(colon + 1).trim();
        if (namePart.isEmpty()) {
            throw new IllegalArgumentException("Group member '" + text + "' has no name");
        }
        try {
            return new GroupMember(namePart, Double.parseDouble(heightPart));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Group member '" + text + "' has an unreadable height: " + heightPart);
        }
    }

    /** @return the member's name */
    public String getName() {
        return name;
    }

    /**
     * @param name the member's name
     * @throws IllegalArgumentException when blank
     */
    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Group member name cannot be empty");
        }
        this.name = name.trim();
    }

    /** @return the member's height in metres */
    public double getHeight() {
        return height;
    }

    /**
     * @param height the member's height in metres
     * @throws IllegalArgumentException when not positive
     */
    public void setHeight(double height) {
        if (height <= 0.0) {
            throw new IllegalArgumentException("Group member height must be positive");
        }
        this.height = height;
    }

    /**
     * @return this member rendered as {@code "Name:1.60"}, the storage format
     */
    public String toStorageString() {
        return name + ":" + String.format("%.2f", height);
    }

    @Override
    public String toString() {
        return name + " (" + String.format("%.2fm", height) + ")";
    }
}
