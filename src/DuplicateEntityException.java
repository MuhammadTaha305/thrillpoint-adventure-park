/**
 * Thrown when an entity with an identifier that is already in use is added to
 * the park.
 *
 * <p>Unchecked: a duplicate identifier means the ID generator is broken, which
 * is a bug rather than a business rule.</p>
 */
public class DuplicateEntityException extends ParkRuntimeException {
    private static final long serialVersionUID = 1L;

    private final String entityId;

    /**
     * @param entityType human-readable type name, e.g. {@code "Ticket"}
     * @param entityId   the identifier that was already taken
     */
    public DuplicateEntityException(String entityType, String entityId) {
        super(entityType + " with id '" + entityId + "' already exists.");
        this.entityId = entityId;
    }

    /** @return the duplicated identifier */
    public String getEntityId() {
        return entityId;
    }
}
