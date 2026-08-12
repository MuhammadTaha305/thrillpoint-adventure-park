import javafx.application.Application;
import javafx.stage.Stage;

/**
 * JavaFX entry point.
 *
 * <p>JavaFX constructs the {@code Application} itself through a no-argument
 * constructor, so the park cannot be passed in normally. {@link Main} therefore
 * hands it over through {@link #setContext} before calling
 * {@link Application#launch}. That keeps {@code Main} as the composition root and
 * this class as a thin adapter.</p>
 */
public class ParkApplication extends Application {

    /** Initial window width. */
    private static final double WIDTH = 1240;

    /** Initial window height. */
    private static final double HEIGHT = 800;

    private static ParkState sharedState;
    private static ParkClock sharedClock;

    private MainWindow window;

    /**
     * Supplies the model before the toolkit starts.
     *
     * @param parkState the restored park
     * @param parkClock the running clock
     */
    public static void setContext(ParkState parkState, ParkClock parkClock) {
        sharedState = parkState;
        sharedClock = parkClock;
    }

    @Override
    public void start(Stage stage) {
        window = new MainWindow(sharedState, sharedClock);

        stage.setTitle("ThrillPoint Adventure Park - Executive Dashboard");
        stage.setScene(window.createScene(WIDTH, HEIGHT));
        stage.setMinWidth(1060);
        stage.setMinHeight(700);

        // Intercept the close button so the park is saved before the window goes.
        stage.setOnCloseRequest(event -> {
            if (window.confirmAndSave()) {
                javafx.application.Platform.exit();
            } else {
                event.consume();
            }
        });

        stage.show();
        AuditLogger.getInstance().log("SYSTEM", "Dashboard opened.");
    }

    @Override
    public void stop() {
        // Platform.exit or a JVM shutdown both land here; the shutdown hook in
        // Main performs the final save and stops the simulation threads.
        System.exit(0);
    }
}
