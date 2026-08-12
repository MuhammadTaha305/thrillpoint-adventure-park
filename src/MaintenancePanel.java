import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Maintenance operations console (FR-07 to FR-09).
 *
 * <p>Three columns: the outstanding task queue, the technician roster with each
 * person's workload, and the safety log. The workload figures make the
 * certification bottleneck visible — only a Senior may work on a roller
 * coaster, so Seniors accumulate far more time on the job than Juniors.</p>
 */
public class MaintenancePanel implements ParkPanel {

    private final ParkState parkState;
    private final BorderPane root = new BorderPane();
    private final ObservableList<String> tasks = FXCollections.observableArrayList();
    private final ObservableList<String> technicians = FXCollections.observableArrayList();
    private final TextArea safetyLog = Theme.textArea(10);

    /**
     * @param parkState the park to service
     */
    public MaintenancePanel(ParkState parkState) {
        this.parkState = parkState;
        build();
    }

    @Override
    public String getTitle() {
        return "Maintenance Operations";
    }

    @Override
    public Parent getView() {
        return root;
    }

    private void build() {
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: " + Theme.BG_DARK + ";");

        // --- task queue -----------------------------------------------------
        ListView<String> taskList = list(tasks);
        Button btnForce = Theme.button("Force Inspection", Theme.ACCENT_RED, "#ff505f");
        btnForce.setMaxWidth(Double.MAX_VALUE);
        btnForce.setOnAction(e -> handleForceInspection());

        VBox queueCard = Theme.card("Maintenance Task Queue");
        VBox.setVgrow(taskList, Priority.ALWAYS);
        queueCard.getChildren().addAll(taskList, btnForce);

        // --- technicians ----------------------------------------------------
        ListView<String> techList = list(technicians);
        VBox techCard = Theme.card("Technicians & Workload");
        VBox.setVgrow(techList, Priority.ALWAYS);
        techCard.getChildren().add(techList);

        // --- safety log -----------------------------------------------------
        safetyLog.setEditable(false);
        safetyLog.setStyle("-fx-control-inner-background: #16181e;"
                         + "-fx-text-fill: #52ff7d;"
                         + "-fx-font-family: 'Consolas', monospace;"
                         + "-fx-font-size: 12;"
                         + "-fx-border-color: " + Theme.BORDER + ";"
                         + "-fx-background-color: " + Theme.BORDER + ";");
        VBox safetyCard = Theme.card("Safety Audit Log");
        VBox.setVgrow(safetyLog, Priority.ALWAYS);
        safetyCard.getChildren().add(safetyLog);

        for (VBox card : new VBox[]{queueCard, techCard, safetyCard}) {
            HBox.setHgrow(card, Priority.ALWAYS);
            card.setPrefWidth(1);
        }

        HBox columns = new HBox(16, queueCard, techCard, safetyCard);
        root.setCenter(columns);
    }

    /**
     * @param items the backing list
     * @return a dark-themed list view
     */
    private ListView<String> list(ObservableList<String> items) {
        ListView<String> view = new ListView<>(items);
        view.setStyle("-fx-background-color: #16181e;"
                    + "-fx-control-inner-background: #16181e;"
                    + "-fx-border-color: " + Theme.BORDER + ";");
        view.setCellFactory(v -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                boolean indented = item != null && item.startsWith("    ");
                setStyle("-fx-background-color: transparent;"
                       + "-fx-text-fill: " + (indented ? Theme.TEXT_MUTED : Theme.TEXT_LIGHT) + ";"
                       + "-fx-font-size: " + (indented ? "11" : "13") + ";");
            }
        });
        return view;
    }

    /** Raises a manager-requested inspection on a chosen ride. */
    private void handleForceInspection() {
        List<Ride> rides = parkState.getRides();
        if (rides.isEmpty()) {
            Dialogs.info("There are no rides to inspect.", "No Rides");
            return;
        }
        List<String> names = new ArrayList<>();
        for (Ride r : rides) {
            names.add(r.getName() + "  (" + r.getStatusString() + ", wear "
                      + String.format("%.0f%%", r.getWear()) + ")");
        }
        String chosen = Dialogs.choose("Force an inspection on which ride?",
                                       "Force Ride Inspection", names);
        if (chosen == null) {
            return;
        }
        Ride target = rides.get(names.indexOf(chosen));

        // Returns false when a task is already outstanding, so clicking twice
        // cannot put two technicians on the same ride.
        if (parkState.forceInspection(target)) {
            Dialogs.info("Inspection raised for " + target.getName()
                         + ". It is now under maintenance.", "Action Confirmed");
        } else {
            Dialogs.warn(target.getName() + " already has an outstanding maintenance task.",
                         "Already Scheduled");
        }
    }

    @Override
    public void refresh() {
        List<String> openTasks = new ArrayList<>();
        for (MaintenanceTask task : parkState.getMaintenanceTasks()) {
            if (task.isActive()) {
                String who = task.getTechnician() != null ? task.getTechnician().getName() : "Unassigned";
                openTasks.add(task.getTaskId() + " - " + task.getRide().getName()
                              + " (" + task.getStatusString() + ")  tech: " + who);
            }
        }
        if (openTasks.isEmpty()) {
            openTasks.add("No outstanding tasks.");
        }
        if (!openTasks.equals(tasks)) {
            tasks.setAll(openTasks);
        }

        List<String> roster = new ArrayList<>();
        for (Technician tech : parkState.getTechnicians()) {
            roster.add(tech.getName() + " (" + tech.getCertificationLevelString() + ")  -  "
                       + (tech.isBusy() ? "BUSY" : "IDLE"));
            roster.add("    " + tech.getRepairsCompleted() + " repairs  ·  "
                       + tech.getBusyTimeString() + " on the job  ·  avg "
                       + String.format("%.1fs", tech.getAverageRepairSeconds()));
        }
        if (!roster.equals(technicians)) {
            technicians.setAll(roster);
        }

        StringBuilder sb = new StringBuilder();
        for (String entry : parkState.getSafetyLogs()) {
            sb.append("- ").append(entry).append("\n");
        }
        String text = sb.toString();
        if (!text.equals(safetyLog.getText())) {
            safetyLog.setText(text);
            safetyLog.positionCaret(text.length());
        }
    }
}
