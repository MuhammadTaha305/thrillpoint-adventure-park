import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * The dashboard shell: header, tab strip and the refresh timeline.
 *
 * <p>Deliberately knows nothing about any individual tab. It holds a list of
 * {@link ParkPanel} and calls three methods on each, so the six tabs can be
 * written and owned independently — adding a seventh is one class plus one line
 * in {@link #buildPanels()}.</p>
 *
 * <p>Everything here runs on the JavaFX application thread. The simulation
 * threads never touch a control; the timeline polls the park through
 * {@link ParkState}'s defensive-copy getters instead, which is why the interface
 * stays responsive while fourteen threads mutate the model.</p>
 */
public class MainWindow {

    /** How often the dashboard re-reads the park. */
    private static final Duration REFRESH_INTERVAL = Duration.seconds(2);

    private final ParkState parkState;
    private final ParkClock parkClock;
    private final List<ParkPanel> panels = new ArrayList<>();

    private final BorderPane root = new BorderPane();
    private final Label lblPeriod = new Label();
    private final Button btnPause = Theme.button("Pause", Theme.ACCENT_ORANGE, "#ffa03c");
    private final ComboBox<String> cmbSpeed = new ComboBox<>();
    private Timeline refreshTimeline;
    private EventLogPanel eventLogPanel;

    /**
     * @param parkState the park to display
     * @param parkClock the clock driving simulated time
     */
    public MainWindow(ParkState parkState, ParkClock parkClock) {
        this.parkState = parkState;
        this.parkClock = parkClock;
        buildPanels();
        buildLayout();
        startRefresh();
    }

    /** Instantiates the six tabs. This is the only place that names them. */
    private void buildPanels() {
        eventLogPanel = new EventLogPanel();
        panels.add(new TicketingPanel(parkState));
        panels.add(new RideStatusPanel(parkState));
        panels.add(new VisitorPanel(parkState));
        panels.add(new MaintenancePanel(parkState));
        panels.add(new ReportPanel(parkState));
        panels.add(eventLogPanel);
    }

    private void buildLayout() {
        root.setStyle("-fx-background-color: " + Theme.BG_DARK + ";");
        root.setTop(buildHeader());

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setStyle("-fx-background-color: " + Theme.BG_DARK + ";");
        for (ParkPanel panel : panels) {
            Tab tab = new Tab(panel.getTitle(), panel.getView());
            tab.setStyle("-fx-font-size: 13; -fx-font-weight: bold;");
            tabs.getTabs().add(tab);
        }
        root.setCenter(tabs);
    }

    /** @return the gradient header with the title, clock badge and controls */
    private Region buildHeader() {
        Label title = new Label("THRILLPOINT ADVENTURE PARK");
        title.setFont(Theme.font(22, true));
        title.setStyle("-fx-text-fill: white;");

        Label subtitle = new Label("Real-time Operations & Ticketing Terminal");
        subtitle.setFont(Theme.font(12, false));
        subtitle.setStyle("-fx-text-fill: " + Theme.TEXT_MUTED + ";");

        VBox titleBlock = new VBox(2, title, subtitle);

        lblPeriod.setFont(Theme.font(13, true));
        lblPeriod.setStyle("-fx-text-fill: " + Theme.TEXT_LIGHT + ";"
                         + "-fx-background-color: #3c4150;"
                         + "-fx-padding: 6 12 6 12; -fx-background-radius: 4;");
        updateClockBadge();

        btnPause.setOnAction(e -> {
            boolean paused = SimulationControl.getInstance().togglePaused();
            btnPause.setText(paused ? "Resume" : "Pause");
            AuditLogger.getInstance().log("SYSTEM",
                paused ? "Simulation paused by operator." : "Simulation resumed by operator.");
        });

        cmbSpeed.getItems().addAll("0.5x", "1x", "2x", "4x", "8x");
        cmbSpeed.getSelectionModel().select(1);
        Theme.style(cmbSpeed);
        cmbSpeed.setPrefWidth(90);
        cmbSpeed.setOnAction(e -> {
            String choice = cmbSpeed.getSelectionModel().getSelectedItem();
            if (choice == null) {
                return;
            }
            double factor = Double.parseDouble(choice.replace("x", ""));
            SimulationControl.getInstance().setSpeedFactor(factor);
            AuditLogger.getInstance().log("SYSTEM", "Simulation speed set to " + choice + ".");
        });

        Label speedLabel = Theme.mutedLabel("Speed:");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox controls = new HBox(10, btnPause, speedLabel, cmbSpeed, lblPeriod);
        controls.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox(titleBlock, spacer, controls);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(15, 25, 15, 25));
        header.setStyle("-fx-background-color: linear-gradient(to right, #12141a, #1c1e26);"
                      + "-fx-border-color: transparent transparent " + Theme.ACCENT_BLUE + " transparent;"
                      + "-fx-border-width: 0 0 2 0;");
        return header;
    }

    /** Refreshes the day, time and pricing period badge. */
    private void updateClockBadge() {
        int period = parkState.getCurrentPeriod();
        lblPeriod.setText("Day " + parkClock.getDayNumber() + "  " + parkClock.getTimeOfDayString()
                          + "   |   " + PricingService.getPeriodString(period)
                          + " (" + PricingService.getPeriodMultiplier(period) + "x)");
    }

    /** Starts the polling timeline that drives every panel's refresh. */
    private void startRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(REFRESH_INTERVAL, e -> {
            updateClockBadge();
            btnPause.setText(SimulationControl.getInstance().isPaused() ? "Resume" : "Pause");
            for (ParkPanel panel : panels) {
                panel.refresh();
            }
        }));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
    }

    /**
     * @param width  initial window width
     * @param height initial window height
     * @return the scene to show
     */
    public Scene createScene(double width, double height) {
        return new Scene(root, width, height);
    }

    /**
     * Confirms exit, then stops the timeline and writes a final save.
     *
     * @return {@code true} when the user confirmed and the park was saved
     */
    public boolean confirmAndSave() {
        if (!Dialogs.confirm("Exit ThrillPoint? The park state will be saved first.", "Confirm Exit")) {
            return false;
        }
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }
        if (eventLogPanel != null) {
            eventLogPanel.dispose();
        }
        AutoSaveService.saveAll(parkState);
        return true;
    }
}
