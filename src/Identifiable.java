/**
 * Role interface for anything the park stores and looks up by a stable string
 * identifier.
 *
 * <p>Interface Segregation: callers that only need an ID depend on this one
 * method rather than on {@code Ticket} or {@code MaintenanceTask}.</p>
 */
public interface Identifiable {

    /** @return the stable, unique identifier of this entity */
    String getId();

    /**
     * Case-insensitive identity comparison, provided as a {@code default} method
     * so no implementor has to repeat it.
     *
     * @param candidateId the identifier to compare against
     * @return {@code true} when {@code candidateId} names this entity
     */
    default boolean hasId(String candidateId) {
        return candidateId != null && candidateId.equalsIgnoreCase(getId());
    }
}
