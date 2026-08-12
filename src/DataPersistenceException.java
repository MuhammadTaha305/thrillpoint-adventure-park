/**
 * Thrown when reading or writing a CSV file or the serialized park snapshot
 * fails.
 *
 * <p>This is the project's exception-translation case: the low-level
 * {@code IOException} / {@code ClassNotFoundException} is wrapped and preserved
 * as {@link #getCause()}, so the stack trace still shows the real cause while
 * callers only have to know about one park-level exception type.</p>
 */
public class DataPersistenceException extends ParkException {
    private static final long serialVersionUID = 1L;

    private final String filepath;

    /**
     * @param filepath the file being read or written
     * @param cause    the underlying I/O or deserialization failure
     */
    public DataPersistenceException(String filepath, Throwable cause) {
        super("Persistence failure on '" + filepath + "': " + cause.getMessage(), cause);
        this.filepath = filepath;
    }

    /** @return the file that could not be read or written */
    public String getFilepath() {
        return filepath;
    }
}
