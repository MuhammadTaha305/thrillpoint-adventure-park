import java.io.Serializable;

/**
 * Root of the {@code Person -> Visitor} and
 * {@code Person -> Staff -> Technician} inheritance chains.
 *
 * <p>Fully encapsulated: fields are {@code private} and every setter validates
 * fail-fast, so a {@code Person} can never exist in an invalid state.</p>
 */
public class Person implements Serializable {
    private static final long serialVersionUID = 2L;

    private String name;
    private int age;

    /**
     * @param name the person's name
     * @param age  the person's age in years
     * @throws IllegalArgumentException when the name is blank or the age negative
     */
    public Person(String name, int age) {
        setName(name);
        setAge(age);
    }

    /** @return the person's name */
    public String getName() {
        return name;
    }

    /**
     * @param name the person's name
     * @throws IllegalArgumentException when blank
     */
    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be empty");
        }
        this.name = name;
    }

    /** @return the person's age in years */
    public int getAge() {
        return age;
    }

    /**
     * @param age the person's age in years
     * @throws IllegalArgumentException when negative
     */
    public void setAge(int age) {
        if (age < 0) {
            throw new IllegalArgumentException("Age cannot be negative");
        }
        this.age = age;
    }

    @Override
    public String toString() {
        return name + " (" + age + ")";
    }
}
