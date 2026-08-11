import javafx.application.Platform;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Singleton audit trail: appends every significant event to
 * {@code audit_log.txt} and pushes it to the live GUI feed (FR-15).
 *
 * <h2>Observer pattern</h2>
 * <p>{@link AuditLogListener} is the observer contract; {@code MainFrame}
 * registers an anonymous implementation and receives every entry on the Swing
 * event dispatch thread.</p>
 *
 * <h2>Why a hand-rolled queue</h2>
 * <p>Simulation threads must never block on disk I/O, so entries are buffered and
 * a daemon writer thread flushes them. The buffer is a plain {@code ArrayList}
 * guarded by {@code synchronized} and drained by polling — deliberately <em>not</em>
 * a concurrent blocking queue from the JDK's concurrency utilities package, which
 * is outside the course scope (only the {@code synchronized} keyword was taught,
 * and the wait/notify pair was not).</p>
 */
public class AuditLogger {

    /** Path of the plain-text audit log. */
    private static final String LOG_FILE = "audit_log.txt";

    /** How long the writer thread waits when the buffer is empty. */
    private static final long POLL_INTERVAL_MS = 200L;

    private static AuditLogger instance;

    private final SimpleDateFormat dateFormat;
    private final List<AuditLogListener> listeners;
    private final List<LogEntry> pending;
    private final Thread writerThread;

    /**
     * Observer contract for anything that wants to see log entries live.
     */
    public interface AuditLogListener {
        /**
         * @param category  the source of the entry, e.g. {@code "TICKETING"}
         * @param message   the entry text
         * @param timestamp the wall-clock time as {@code "HH:mm:ss"}
         */
        void onLogAdded(String category, String message, String timestamp);
    }

    private AuditLogger() {
        dateFormat = new SimpleDateFormat("HH:mm:ss");
        listeners = new ArrayList<>();
        pending = new ArrayList<>();

        writerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    List<LogEntry> batch = drainPending();
                    if (batch.isEmpty()) {
                        try {
                            Thread.sleep(POLL_INTERVAL_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        continue;
                    }
                    writeBatch(batch);
                    publish(batch);
                }
            }
        }, "AuditLogger-Writer");
        writerThread.setDaemon(true);
        writerThread.start();
    }

    /**
     * @return the singleton instance, created on first use
     */
    public static synchronized AuditLogger getInstance() {
        if (instance == null) {
            instance = new AuditLogger();
        }
        return instance;
    }

    /**
     * @param listener the observer to notify on every future entry
     */
    public synchronized void addListener(AuditLogListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * @param listener the observer to stop notifying
     */
    public synchronized void removeListener(AuditLogListener listener) {
        listeners.remove(listener);
    }

    /**
     * Records an event. Returns immediately; the disk write happens on the writer
     * thread.
     *
     * @param category the source of the entry, e.g. {@code "MAINTENANCE"}
     * @param message  the entry text
     */
    public synchronized void log(String category, String message) {
        String timestamp = dateFormat.format(new Date());
        String formatted = "[" + timestamp + "] [" + category + "] " + message;
        pending.add(new LogEntry(category, message, timestamp, formatted));
    }

    /**
     * @return everything buffered since the last call, leaving the buffer empty
     */
    private synchronized List<LogEntry> drainPending() {
        if (pending.isEmpty()) {
            return new ArrayList<>();
        }
        List<LogEntry> batch = new ArrayList<>(pending);
        pending.clear();
        return batch;
    }

    /**
     * @return a snapshot of the registered observers
     */
    private synchronized List<AuditLogListener> listenerSnapshot() {
        return new ArrayList<>(listeners);
    }

    /**
     * Appends a whole batch in one file open.
     *
     * @param batch the entries to write
     */
    private void writeBatch(List<LogEntry> batch) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {
            for (LogEntry entry : batch) {
                pw.println(entry.formattedLog);
            }
        } catch (IOException e) {
            System.err.println("Failed to write to audit log file: " + e.getMessage());
        }
    }

    /**
     * Hands a batch to every observer on the Swing event dispatch thread.
     *
     * @param batch the entries to publish
     */
    private void publish(List<LogEntry> batch) {
        List<AuditLogListener> snapshot = listenerSnapshot();
        if (snapshot.isEmpty()) {
            return;
        }
        for (final LogEntry entry : batch) {
            for (final AuditLogListener listener : snapshot) {
                try {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {
                            listener.onLogAdded(entry.category, entry.message, entry.timestamp);
                        }
                    });
                } catch (IllegalStateException e) {
                    // The JavaFX toolkit is not running - either the log fired
                    // during startup before launch(), or the window has closed.
                    // The entry is already on disk, so dropping the UI copy is
                    // the correct behaviour rather than an error.
                    return;
                }
            }
        }
    }

    /** Immutable value object for one buffered entry. */
    private static class LogEntry {
        private final String category;
        private final String message;
        private final String timestamp;
        private final String formattedLog;

        private LogEntry(String category, String message, String timestamp, String formattedLog) {
            this.category = category;
            this.message = message;
            this.timestamp = timestamp;
            this.formattedLog = formattedLog;
        }
    }
}
