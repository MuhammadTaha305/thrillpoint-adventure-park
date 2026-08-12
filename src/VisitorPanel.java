import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Visitor and ticket lookup, and the re-boarding desk.
 *
 * <p>Searching aside, this tab exists to make Day Passes and VIP Fast Passes mean
 * what they say: a holder can be put back into a queue on the ticket they already
 * have, at no charge. Without it every ride required buying a new ticket, so
 * "unlimited rides" was never actually reachable.</p>
 */
public class VisitorPanel implements ParkPanel {

    private final ParkState parkState;
    private final BorderPane root = new BorderPane();
    private final TextField txtSearch = Theme.textField();
    private final TableView<Visitor> table = new TableView<>();
    private final ObservableList<Visitor> rows = FXCollections.observableArrayList();
    private final ComboBox<String> cmbRide = new ComboBox<>();
    private final Label lblSummary = Theme.mutedLabel("Select a visitor above to board them again.");

    /** Ride name each listed visitor is currently queued for, parallel to {@code rows}. */
    private final List<String> queuedOn = new ArrayList<>();

    /**
     * @param parkState the park to search
     */
    public VisitorPanel(ParkState parkState) {
        this.parkState = parkState;
        build();
    }

    @Override
    public String getTitle() {
        return "Visitors & Tickets";
    }

    @Override
    public Parent getView() {
        return root;
    }

    private void build() {
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: " + Theme.BG_DARK + ";");

        txtSearch.setPromptText("visitor name or ticket ID");
        HBox.setHgrow(txtSearch, Priority.ALWAYS);
        HBox search = new HBox(12, Theme.formLabel("Search:"), txtSearch);
        search.setAlignment(Pos.CENTER_LEFT);
        search.setPadding(new Insets(0, 0, 14, 0));
        txtSearch.textProperty().addListener((o, a, b) -> reload());
        root.setTop(search);

        table.setItems(rows);
        table.setPlaceholder(Theme.mutedLabel("No visitors match."));
        Theme.styleTable(table);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        table.getColumns().add(col("Visitor", 130, v -> v.getName()));
        table.getColumns().add(col("Age", 50, v -> String.valueOf(v.getAge())));
        table.getColumns().add(col("Height", 70, v -> String.format("%.2fm", v.getHeight())));
        table.getColumns().add(col("Ticket ID", 90,
            v -> v.getTicket() == null ? "-" : v.getTicket().getTicketId()));
        table.getColumns().add(col("Ticket Type", 110,
            v -> v.getTicket() == null ? "-" : v.getTicket().getTicketTypeString()));
        table.getColumns().add(col("Paid", 80,
            v -> v.getTicket() == null ? "-" : String.format("$%.2f", v.getTicket().getCurrentPrice())));
        table.getColumns().add(col("Ticket Status", 100, v -> describe(v.getTicket())));

        TableColumn<Visitor, String> waiting = new TableColumn<>("Waiting For");
        waiting.setPrefWidth(130);
        waiting.setCellValueFactory(cd -> {
            int i = rows.indexOf(cd.getValue());
            return new SimpleStringProperty(i >= 0 && i < queuedOn.size() ? queuedOn.get(i) : "-");
        });
        table.getColumns().add(waiting);

        root.setCenter(table);

        Theme.style(cmbRide);
        cmbRide.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cmbRide, Priority.ALWAYS);

        Button btnBoard = Theme.button("Board on Selected Ride (no charge)",
                                       Theme.ACCENT_GREEN, "#32be55");
        btnBoard.setOnAction(e -> handleBoard());

        HBox boardRow = new HBox(12, Theme.formLabel("Ride:"), cmbRide, btnBoard);
        boardRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = Theme.card();
        card.getChildren().addAll(lblSummary, boardRow);
        card.setPadding(new Insets(14, 16, 14, 16));
        BorderPane.setMargin(card, new Insets(16, 0, 0, 0));
        root.setBottom(card);

        table.getSelectionModel().selectedItemProperty().addListener((o, a, v) -> {
            if (v == null) {
                lblSummary.setText("Select a visitor above to board them again.");
            } else if (v.getTicket() == null) {
                lblSummary.setText(v.getName() + " holds no ticket and cannot board.");
            } else {
                lblSummary.setText(v.getName() + " holds " + v.getTicket().getTicketId()
                                   + " (" + v.getTicket().getTicketTypeString() + ") - "
                                   + describe(v.getTicket()).toLowerCase() + ".");
            }
        });

        reload();
    }

    /**
     * @param title  column heading
     * @param width  preferred width
     * @param reader extracts the display string
     * @return the configured column
     */
    private TableColumn<Visitor, String> col(String title, double width, VisitorText reader) {
        TableColumn<Visitor, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(cd -> new SimpleStringProperty(reader.read(cd.getValue())));
        return c;
    }

    /**
     * @param t the ticket to summarise
     * @return a short validity description
     */
    private String describe(Ticket t) {
        if (t == null) {
            return "No ticket";
        }
        if (t.isExpired()) {
            return "Expired";
        }
        if (t instanceof SingleRideTicket && ((SingleRideTicket) t).isUsed()) {
            return "Used";
        }
        return "Valid";
    }

    /** Rebuilds the result list from the park and the current search text. */
    private void reload() {
        String needle = txtSearch.getText() == null ? "" : txtSearch.getText().trim().toLowerCase();
        Visitor selected = table.getSelectionModel().getSelectedItem();

        List<Ride> rides = parkState.getRides();
        List<Visitor> matches = new ArrayList<>();
        List<String> waitingFor = new ArrayList<>();

        for (Visitor v : parkState.getRegisteredVisitors()) {
            Ticket t = v.getTicket();
            boolean hit = needle.isEmpty()
                || v.getName().toLowerCase().contains(needle)
                || (t != null && t.getTicketId().toLowerCase().contains(needle));
            if (!hit) {
                continue;
            }
            String where = "-";
            for (Ride ride : rides) {
                boolean found = false;
                for (Visitor queued : ride.getQueue()) {
                    if (queued == v) {
                        found = true;
                        break;
                    }
                }
                if (found) {
                    where = ride.getName();
                    break;
                }
            }
            matches.add(v);
            waitingFor.add(where);
        }

        queuedOn.clear();
        queuedOn.addAll(waitingFor);
        rows.setAll(matches);
        if (selected != null && matches.contains(selected)) {
            table.getSelectionModel().select(selected);
        }

        List<String> names = new ArrayList<>();
        for (Ride r : rides) {
            names.add(r.getName());
        }
        if (!names.equals(cmbRide.getItems())) {
            String keep = cmbRide.getSelectionModel().getSelectedItem();
            cmbRide.getItems().setAll(names);
            if (keep != null && names.contains(keep)) {
                cmbRide.getSelectionModel().select(keep);
            } else if (!names.isEmpty()) {
                cmbRide.getSelectionModel().select(0);
            }
        }
    }

    /** Queues the selected visitor on the chosen ride using their existing ticket. */
    private void handleBoard() {
        Visitor visitor = table.getSelectionModel().getSelectedItem();
        if (visitor == null) {
            Dialogs.info("Select a visitor in the table first.", "No Visitor Selected");
            return;
        }
        Ride ride = parkState.findRideByName(cmbRide.getSelectionModel().getSelectedItem());
        if (ride == null) {
            Dialogs.warn("That selection is no longer valid.", "Stale Selection");
            return;
        }

        try {
            TicketingService.boardExistingVisitor(parkState, visitor, ride);
            Dialogs.info(visitor.getName() + " is queued for " + ride.getName()
                         + " on ticket " + visitor.getTicket().getTicketId() + ". No charge.",
                         "Boarded");
            reload();
        } catch (TicketExpiredException e) {
            Dialogs.warn(e.getMessage(), "Ticket Expired");
        } catch (HeightRestrictionException e) {
            Dialogs.warn(e.getMessage(), "Safety Restriction");
        } catch (MaintenanceInProgressException e) {
            Dialogs.warn(e.getMessage(), "Ride Under Maintenance");
        } catch (RideClosedException e) {
            Dialogs.warn(e.getMessage(), "Ride Closed");
        } catch (CapacityExceededException e) {
            Dialogs.warn(e.getMessage(), "Queue Full");
        } catch (ParkException e) {
            Dialogs.warn(e.getMessage(), "Cannot Board");
        }
    }

    @Override
    public void refresh() {
        reload();
    }

    /** Extracts a display string from a visitor. */
    private interface VisitorText {
        /**
         * @param visitor the row's visitor
         * @return the text to display
         */
        String read(Visitor visitor);
    }
}
