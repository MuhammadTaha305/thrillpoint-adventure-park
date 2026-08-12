import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.List;

/**
 * Live ride monitor (FR-16).
 *
 * <p>Shows every ride's status, wear, throughput and availability, and gives
 * management its two direct levers: cancelling a queued booking (FR-05) and
 * closing or reopening a ride (FR-09).</p>
 */
public class RideStatusPanel implements ParkPanel {

    private final ParkState parkState;
    private final BorderPane root = new BorderPane();
    private final TableView<Ride> table = new TableView<>();
    private final ObservableList<Ride> rows = FXCollections.observableArrayList();

    /**
     * @param parkState the park to monitor
     */
    public RideStatusPanel(ParkState parkState) {
        this.parkState = parkState;
        build();
    }

    @Override
    public String getTitle() {
        return "Rides Monitor";
    }

    @Override
    public Parent getView() {
        return root;
    }

    private void build() {
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: " + Theme.BG_DARK + ";");

        table.setItems(rows);
        table.setPlaceholder(Theme.mutedLabel("No rides loaded."));
        Theme.styleTable(table);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.getColumns().add(text("Ride Name", 150, r -> r.getName()));
        table.getColumns().add(text("Zone", 110, r -> r.getZone()));
        table.getColumns().add(text("Cap", 50, r -> String.valueOf(r.getCapacity())));
        table.getColumns().add(text("Min Height", 80, r -> String.format("%.2fm", r.getMinHeight())));
        table.getColumns().add(wearColumn());
        table.getColumns().add(text("Riders", 65, r -> String.valueOf(r.getRidersServed())));
        table.getColumns().add(text("Revenue", 90, r -> String.format("$%.2f", r.getRevenue())));
        table.getColumns().add(text("Status", 95, r -> r.getStatusString()));
        table.getColumns().add(text("Uptime", 70, r -> String.format("%.0f%%", r.getUptimePercent())));
        table.getColumns().add(text("Load", 60, r -> String.format("%.0f%%", r.getLoadFactorPercent())));
        table.getColumns().add(text("Downtime", 80, r -> r.getDowntimeString()));

        root.setCenter(table);

        Button btnCancel = Theme.button("Cancel a Booking", Theme.ACCENT_ORANGE, "#ffa03c");
        btnCancel.setOnAction(e -> handleCancelBooking());
        Button btnClose = Theme.button("Close Selected Ride", Theme.ACCENT_RED, "#ff505f");
        btnClose.setOnAction(e -> handleCloseRide());
        Button btnReopen = Theme.button("Reopen Selected Ride", Theme.ACCENT_GREEN, "#32be55");
        btnReopen.setOnAction(e -> handleReopenRide());

        for (Button b : new Button[]{btnCancel, btnClose, btnReopen}) {
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }
        HBox actions = new HBox(12, btnCancel, btnClose, btnReopen);
        actions.setPadding(new Insets(16, 0, 0, 0));
        root.setBottom(actions);
    }

    /**
     * Builds a plain text column.
     *
     * @param title  column heading
     * @param width  preferred width
     * @param reader extracts the display string from a ride
     * @return the configured column
     */
    private TableColumn<Ride, String> text(String title, double width, RideText reader) {
        TableColumn<Ride, String> col = new TableColumn<>(title);
        col.setPrefWidth(width);
        col.setCellValueFactory(cd -> new SimpleStringProperty(reader.read(cd.getValue())));
        return col;
    }

    /** @return the wear column, rendered as a colour-coded progress bar */
    private TableColumn<Ride, Double> wearColumn() {
        TableColumn<Ride, Double> col = new TableColumn<>("Wear Level");
        col.setPrefWidth(110);
        col.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().getWear()));
        col.setCellFactory(c -> new TableCell<Ride, Double>() {
            private final ProgressBar bar = new ProgressBar(0);
            private final Label label = new Label();
            private final StackPane stack = new StackPane(bar, label);

            @Override
            protected void updateItem(Double value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setGraphic(null);
                    return;
                }
                bar.setProgress(value / 100.0);
                bar.setPrefWidth(100);
                String colour = value >= Maintainable.WEAR_THRESHOLD ? Theme.ACCENT_RED
                              : value >= 35.0 ? Theme.ACCENT_ORANGE : Theme.ACCENT_GREEN;
                bar.setStyle("-fx-accent: " + colour + ";");
                label.setText(String.format("%.0f%%", value));
                label.setStyle("-fx-text-fill: white; -fx-font-size: 10; -fx-font-weight: bold;");
                setGraphic(stack);
            }
        });
        return col;
    }

    /** @return the selected ride, or {@code null} when nothing is selected */
    private Ride selectedRide() {
        return table.getSelectionModel().getSelectedItem();
    }

    /** Removes one visitor from a ride queue and refunds where appropriate. */
    private void handleCancelBooking() {
        List<Ride> rides = parkState.getRides();
        if (rides.isEmpty()) {
            Dialogs.info("There are no rides to cancel from.", "Nothing to Cancel");
            return;
        }

        List<String> rideNames = new ArrayList<>();
        for (Ride r : rides) {
            rideNames.add(r.getName() + "  (" + r.getQueueSize() + " waiting)");
        }
        String chosen = Dialogs.choose("Cancel a booking on which ride?", "Cancel Booking", rideNames);
        if (chosen == null) {
            return;
        }
        Ride ride = rides.get(rideNames.indexOf(chosen));

        List<Visitor> queue = ride.getQueue();
        if (queue.isEmpty()) {
            Dialogs.info("Nobody is waiting for " + ride.getName() + ".", "Empty Queue");
            return;
        }

        List<String> waiting = new ArrayList<>();
        for (Visitor v : queue) {
            String ticket = v.getTicket() != null ? v.getTicket().getTicketId() : "no ticket";
            waiting.add(v.getName() + "  [" + ticket + "]");
        }
        String who = Dialogs.choose("Remove which visitor from the queue?", "Cancel Booking", waiting);
        if (who == null) {
            return;
        }

        try {
            boolean cancelled = TicketingService.cancelBooking(
                parkState, ride, queue.get(waiting.indexOf(who)).getName());
            if (cancelled) {
                Dialogs.info("Booking cancelled and the seat returned to the pool.", "Cancelled");
            } else {
                Dialogs.warn("That visitor had already left the queue.", "Nothing to Cancel");
            }
        } catch (ParkException e) {
            Dialogs.error(e.getMessage(), "Cancellation Failed");
        }
    }

    /** Closes the selected ride by management decision. */
    private void handleCloseRide() {
        Ride ride = selectedRide();
        if (ride == null) {
            Dialogs.info("Select a ride in the table first.", "No Ride Selected");
            return;
        }
        if (ride.getStatus() == Ride.STATUS_UNDER_MAINTENANCE) {
            Dialogs.warn(ride.getName() + " is under maintenance. Let the crew finish first.",
                         "Under Maintenance");
            return;
        }
        if (parkState.closeRide(ride)) {
            Dialogs.info(ride.getName() + " is now closed and will take no further bookings.",
                         "Ride Closed");
        } else {
            Dialogs.warn(ride.getName() + " is already closed.", "No Change");
        }
    }

    /** Reopens a ride that management closed. */
    private void handleReopenRide() {
        Ride ride = selectedRide();
        if (ride == null) {
            Dialogs.info("Select a ride in the table first.", "No Ride Selected");
            return;
        }
        if (parkState.reopenRide(ride)) {
            Dialogs.info(ride.getName() + " is open again.", "Ride Reopened");
        } else {
            Dialogs.warn(ride.getName() + " is not a management closure. Only a completed repair "
                         + "can reopen a ride that is under maintenance.", "No Change");
        }
    }

    @Override
    public void refresh() {
        List<Ride> current = parkState.getRides();
        if (current.size() != rows.size() || !rows.containsAll(current)) {
            Ride selected = selectedRide();
            rows.setAll(current);
            if (selected != null) {
                table.getSelectionModel().select(selected);
            }
        } else {
            // Same ride objects, changed values - re-run the cell factories
            // without disturbing the selection.
            table.refresh();
        }
    }

    /** Extracts a display string from a ride. */
    private interface RideText {
        /**
         * @param ride the row's ride
         * @return the text to display
         */
        String read(Ride ride);
    }
}
