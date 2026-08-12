import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Management analytics (FR-17).
 *
 * <p>Revenue per ride and riders per zone as bar charts, plus the day-by-day
 * history that {@link ParkClock} files at each park close. Uses JavaFX's built-in
 * {@code BarChart} rather than hand-drawn graphics, which is both less code and
 * properly resizable.</p>
 */
public class ReportPanel implements ParkPanel {

    /** Where the exported summary is written. */
    private static final String REPORT_FILE = "park_reports.txt";

    private final ParkState parkState;
    private final BorderPane root = new BorderPane();

    private final CategoryAxis rideAxis = new CategoryAxis();
    private final NumberAxis revenueAxis = new NumberAxis();
    private final BarChart<String, Number> revenueChart = new BarChart<>(rideAxis, revenueAxis);
    private final XYChart.Series<String, Number> revenueSeries = new XYChart.Series<>();

    private final CategoryAxis zoneAxis = new CategoryAxis();
    private final NumberAxis ridersAxis = new NumberAxis();
    private final BarChart<String, Number> zoneChart = new BarChart<>(zoneAxis, ridersAxis);
    private final XYChart.Series<String, Number> zoneSeries = new XYChart.Series<>();

    private final TableView<DayRecord> historyTable = new TableView<>();
    private final ObservableList<DayRecord> history = FXCollections.observableArrayList();

    /**
     * @param parkState the park to report on
     */
    public ReportPanel(ParkState parkState) {
        this.parkState = parkState;
        build();
    }

    @Override
    public String getTitle() {
        return "Analytics Reports";
    }

    @Override
    public Parent getView() {
        return root;
    }

    private void build() {
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: " + Theme.BG_DARK + ";");

        styleChart(revenueChart, "Revenue by Ride ($)", revenueSeries);
        styleChart(zoneChart, "Riders Served by Zone", zoneSeries);

        HBox.setHgrow(revenueChart, Priority.ALWAYS);
        HBox.setHgrow(zoneChart, Priority.ALWAYS);
        revenueChart.setPrefWidth(1);
        zoneChart.setPrefWidth(1);
        HBox charts = new HBox(16, revenueChart, zoneChart);
        root.setCenter(charts);

        // --- daily history --------------------------------------------------
        historyTable.setItems(history);
        historyTable.setPlaceholder(Theme.mutedLabel("No completed days yet."));
        historyTable.setPrefHeight(170);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Theme.styleTable(historyTable);

        historyTable.getColumns().add(col("Day", d -> "Day " + d.getDayNumber()));
        historyTable.getColumns().add(col("Revenue", d -> String.format("$%.2f", d.getRevenue())));
        historyTable.getColumns().add(col("Riders", d -> String.valueOf(d.getRidersServed())));
        historyTable.getColumns().add(col("Tickets Sold", d -> String.valueOf(d.getTicketsSold())));
        historyTable.getColumns().add(col("Repairs", d -> String.valueOf(d.getRepairsCompleted())));
        historyTable.getColumns().add(col("Revenue / Rider",
            d -> String.format("$%.2f", d.getRevenuePerRider())));

        Button btnExport = Theme.button("Export Management Summary", Theme.ACCENT_GREEN, "#32be55");
        btnExport.setMaxWidth(Double.MAX_VALUE);
        btnExport.setOnAction(e -> exportReport());

        Button btnDemo = Theme.primaryButton("Run Race Condition Demo");
        btnDemo.setMaxWidth(Double.MAX_VALUE);
        btnDemo.setOnAction(e -> runRaceDemo());

        HBox.setHgrow(btnExport, Priority.ALWAYS);
        HBox.setHgrow(btnDemo, Priority.ALWAYS);
        HBox actions = new HBox(16, btnExport, btnDemo);

        VBox bottom = new VBox(14, Theme.heading("Daily History"), historyTable, actions);
        bottom.setPadding(new Insets(16, 0, 0, 0));
        root.setBottom(bottom);
    }

    /**
     * @param title  column heading
     * @param reader extracts the display string
     * @return the configured column
     */
    private TableColumn<DayRecord, String> col(String title, DayText reader) {
        TableColumn<DayRecord, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(reader.read(cd.getValue())));
        return c;
    }

    /**
     * @param chart  the chart to configure
     * @param title  chart heading
     * @param series the single data series it displays
     */
    private void styleChart(BarChart<String, Number> chart, String title,
                            XYChart.Series<String, Number> series) {
        chart.setTitle(title);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.getData().add(series);
        chart.setStyle("-fx-background-color: " + Theme.BG_CARD + ";"
                     + "-fx-border-color: " + Theme.BORDER + ";"
                     + "-fx-text-fill: " + Theme.TEXT_LIGHT + ";");
        chart.lookupAll(".chart-title").forEach(n ->
            n.setStyle("-fx-text-fill: " + Theme.TEXT_LIGHT + "; -fx-font-size: 14; -fx-font-weight: bold;"));
    }

    /** Writes the full management summary to a text file. */
    private void exportReport() {
        try (PrintWriter pw = new PrintWriter(new java.io.FileWriter(REPORT_FILE))) {
            pw.println("=================================================");
            pw.println("      THRILLPOINT ADVENTURE PARK REPORT");
            pw.println("      Generated: "
                       + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            pw.println("=================================================");
            pw.println();
            pw.println("Total Park Revenue: $" + String.format("%.2f", parkState.getTotalRevenue()));
            pw.println("Total Riders Served: " + parkState.getTotalRidersServed());
            pw.println();

            pw.println("--- Zone Summary ---");
            for (Zone z : parkState.getZones()) {
                pw.println("- " + z.getName() + ":");
                pw.println("  * Rides open: " + z.getOperationalRideCount() + " of " + z.getRideCount());
                pw.println("  * Riders served: " + z.getTotalRidersServed());
                pw.println("  * Revenue: $" + String.format("%.2f", z.getTotalRevenue()));
                pw.println("  * Average uptime: " + String.format("%.1f%%", z.getAverageUptimePercent()));
                pw.println("  * Currently queued: " + z.getTotalQueueSize());
            }
            pw.println();

            pw.println("--- Ride Performance ---");
            for (Ride r : parkState.getRides()) {
                pw.println("- " + r.getName() + " (" + r.getZone() + ", " + r.getRideType() + "):");
                pw.println("  * Status: " + r.getStatusString());
                pw.println("  * Current wear: " + String.format("%.1f%%", r.getWear()));
                pw.println("  * Riders served: " + r.getRidersServed());
                pw.println("  * Revenue: $" + String.format("%.2f", r.getRevenue()));
                pw.println("  * Cycles run: " + r.getCyclesRun());
                pw.println("  * Load factor: " + String.format("%.1f%%", r.getLoadFactorPercent()));
                pw.println("  * Uptime: " + String.format("%.1f%%", r.getUptimePercent())
                           + " (offline " + r.getDowntimeString() + ")");
            }
            pw.println();

            pw.println("--- Daily History ---");
            List<DayRecord> days = parkState.getDayHistory();
            if (days.isEmpty()) {
                pw.println("No completed days yet.");
            } else {
                for (DayRecord d : days) {
                    pw.println("- " + d);
                }
            }
            pw.println();

            pw.println("--- Technician Workload ---");
            for (Technician t : parkState.getTechnicians()) {
                pw.println("- " + t.getName() + " (" + t.getCertificationLevelString() + "): "
                           + t.getRepairsCompleted() + " repairs, " + t.getBusyTimeString()
                           + " on the job, avg "
                           + String.format("%.1fs", t.getAverageRepairSeconds()));
            }
            pw.println();
            pw.println("Total completed repairs: " + parkState.getRepairsCompleted());
            pw.println("Total safety log entries: " + parkState.getSafetyLogs().size());

            Dialogs.info("Report written to " + REPORT_FILE + ".", "Export Complete");
            AuditLogger.getInstance().log("SYSTEM", "Manager exported the report summary.");

        } catch (IOException e) {
            Dialogs.error("Could not write the report: " + e.getMessage(), "Export Failed");
        }
    }

    /**
     * Runs the race condition demonstration off the FX thread and shows the
     * result (FR-18).
     */
    private void runRaceDemo() {
        TextArea output = Theme.textArea(20);
        output.setEditable(false);
        output.setText("Running 3,200 concurrent booking attempts...\n");
        output.setStyle("-fx-control-inner-background: #16181e;"
                      + "-fx-text-fill: " + Theme.TEXT_LIGHT + ";"
                      + "-fx-font-family: 'Consolas', monospace; -fx-font-size: 13;");

        Stage window = new Stage();
        window.setTitle("Race Condition Demonstration");
        VBox box = new VBox(output);
        VBox.setVgrow(output, Priority.ALWAYS);
        box.setStyle("-fx-background-color: " + Theme.BG_DARK + ";");
        window.setScene(new javafx.scene.Scene(box, 640, 480));
        window.show();

        // The demo spawns 16 threads and joins them, so it must not run on the
        // FX application thread or the window would freeze.
        Task<String> task = new Task<String>() {
            @Override
            protected String call() {
                return RaceConditionDemo.runDemo();
            }
        };
        task.setOnSucceeded(e -> {
            output.setText(task.getValue());
            AuditLogger.getInstance().log("SYSTEM", "Race condition demonstration completed.");
        });
        task.setOnFailed(e -> output.setText("Demo failed: " + task.getException()));

        Thread runner = new Thread(task, "RaceConditionDemo");
        runner.setDaemon(true);
        runner.start();
    }

    @Override
    public void refresh() {
        revenueSeries.getData().clear();
        for (Ride r : parkState.getRides()) {
            revenueSeries.getData().add(new XYChart.Data<>(r.getName(), r.getRevenue()));
        }

        zoneSeries.getData().clear();
        for (Zone z : parkState.getZones()) {
            zoneSeries.getData().add(new XYChart.Data<>(z.getName(), z.getTotalRidersServed()));
        }

        List<DayRecord> days = parkState.getDayHistory();
        if (days.size() != history.size()) {
            history.setAll(days);
        }
    }

    /** Extracts a display string from a day record. */
    private interface DayText {
        /**
         * @param record the row's record
         * @return the text to display
         */
        String read(DayRecord record);
    }
}
