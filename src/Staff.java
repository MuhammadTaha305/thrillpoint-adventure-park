/**
 * A park employee — the middle level of the
 * {@code Person -> Staff -> Technician} inheritance chain.
 */
public class Staff extends Person {
    private static final long serialVersionUID = 2L;

    private String employeeId;

    /**
     * @param name       the employee's name
     * @param age        the employee's age in years
     * @param employeeId the staff identifier, e.g. {@code "T-01"}
     * @throws IllegalArgumentException when the identifier is blank
     */
    public Staff(String name, int age, String employeeId) {
        super(name, age);
        setEmployeeId(employeeId);
    }

    /** @return the staff identifier */
    public String getEmployeeId() {
        return employeeId;
    }

    /**
     * @param employeeId the staff identifier
     * @throws IllegalArgumentException when blank
     */
    public void setEmployeeId(String employeeId) {
        if (employeeId == null || employeeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Employee ID cannot be empty");
        }
        this.employeeId = employeeId;
    }

    @Override
    public String toString() {
        return getName() + " [" + employeeId + "]";
    }
}
