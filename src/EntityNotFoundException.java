/**
 * Thrown when a lookup by identifier or name finds nothing.
 *
 * <p>Unchecked: every caller in this system looks entities up from a list it
 * just read, so a miss indicates a bug rather than a user error.</p>
 */
public class EntityNotFoundException extends ParkRuntimeException {
    private static final long serialVersionUID = 1L;

    private final String entityId;

    /**
     * @param entityType human-readable type name, e.g. {@code "Ride"}
     * @param entityId   the identifier or name that was not found
     */
    public EntityNotFoundException(String entityType, String entityId) {
        super(entityType + " '" + entityId + "' was not found.");
        this.entityId = entityId;
    }

    /** @return the identifier that was not found */
    public String getEntityId() {
        return entityId;
    }
}
