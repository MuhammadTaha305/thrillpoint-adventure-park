import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The ticket desk (FR-01 to FR-04).
 *
 * <p>Collects visitor details, previews the price under the active pricing
 * period, and sells a ticket. Every refusal from {@link TicketingService} is a
 * typed {@link ParkException}, mapped here to its own dialog title.</p>
 */
public class TicketingPanel implements ParkPanel {

    private final ParkState parkState;
    private final BorderPane root = new BorderPane();

    private final TextField txtName = Theme.textField();
    private final TextField txtAge = Theme.textField();
    private final TextField txtHeight = Theme.textField();
    private final TextArea txtGroupMembers = Theme.textArea(3);
    private final ComboBox<String> cmbTicketType = new ComboBox<>();
    private final ComboBox<String> cmbRide = new ComboBox<>();
    private final Label lblPrice = new Label("Estimated Price: --");

    /**
     * @param parkState the park to sell tickets into
     */
    public TicketingPanel(ParkState parkState) {
        this.parkState = parkState;
        build();
    }

    @Override
    public String getTitle() {
        return "Ticketing Counter";
    }

    @Override
    public Parent getView() {
        return root;
    }

    private void build() {
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: " + Theme.BG_DARK + ";");

        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(12);

        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(210);
        ColumnConstraints inputs = new ColumnConstraints();
        inputs.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(labels, inputs);

        cmbTicketType.getItems().addAll(
            "Single Ride Ticket", "Day Pass Ticket", "VIP Fast Pass Ticket", "Group Ticket");
        cmbTicketType.getSelectionModel().select(0);
        Theme.style(cmbTicketType);
        cmbTicketType.setMaxWidth(Double.MAX_VALUE);

        Theme.style(cmbRide);
        cmbRide.setMaxWidth(Double.MAX_VALUE);
        refreshRideCombo();

        txtGroupMembers.setDisable(true);
        txtGroupMembers.setPromptText("Ali:1.55; Sara:1.40; Bilal");

        form.add(Theme.formLabel("Visitor Name:"), 0, 0);
        form.add(txtName, 1, 0);
        form.add(Theme.formLabel("Visitor Age:"), 0, 1);
        form.add(txtAge, 1, 1);
        form.add(Theme.formLabel("Height (metres):"), 0, 2);
        form.add(txtHeight, 1, 2);
        form.add(Theme.formLabel("Ticket Type:"), 0, 3);
        form.add(cmbTicketType, 1, 3);
        form.add(Theme.formLabel("Target Ride:"), 0, 4);
        form.add(cmbRide, 1, 4);

        VBox groupLabel = new VBox(2,
            Theme.formLabel("Group Members:"),
            Theme.mutedLabel("name:height, separated by ;"),
            Theme.mutedLabel("height optional, defaults to "
                             + String.format("%.2f", GroupMember.DEFAULT_HEIGHT) + "m"));
        form.add(groupLabel, 0, 5);
        form.add(txtGroupMembers, 1, 5);

        VBox card = Theme.card();
        card.getChildren().add(form);
        VBox.setVgrow(form, Priority.NEVER);
        root.setCenter(card);
        BorderPane.setMargin(card, new Insets(0, 0, 16, 0));

        // --- price preview and book button --------------------------------
        lblPrice.setFont(Theme.font(18, true));
        lblPrice.setStyle("-fx-text-fill: #52c86e;");

        Button btnBook = Theme.primaryButton("Buy & Book Seat");
        btnBook.setOnAction(e -> handleBooking());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(12, lblPrice, spacer, btnBook);
        bar.setAlignment(Pos.CENTER_LEFT);
        VBox bottom = Theme.card();
        bottom.getChildren().add(bar);
        root.setBottom(bottom);

        // Live price updates
        cmbTicketType.setOnAction(e -> {
            boolean isGroup = cmbTicketType.getSelectionModel().getSelectedIndex() == Ticket.TYPE_GROUP;
            txtGroupMembers.setDisable(!isGroup);
            updatePricePreview();
        });
        cmbRide.setOnAction(e -> updatePricePreview());
        txtGroupMembers.textProperty().addListener((o, a, b) -> updatePricePreview());

        updatePricePreview();
    }

    /** Reloads the ride list, preserving the current selection. */
    private void refreshRideCombo() {
        String selected = cmbRide.getSelectionModel().getSelectedItem();
        List<String> names = new ArrayList<>();
        for (Ride r : parkState.getRides()) {
            names.add(r.getName());
        }
        if (!names.equals(cmbRide.getItems())) {
            cmbRide.getItems().setAll(names);
            if (selected != null && names.contains(selected)) {
                cmbRide.getSelectionModel().select(selected);
            } else if (!names.isEmpty()) {
                cmbRide.getSelectionModel().select(0);
            }
        }
    }

    /** Recalculates the quoted price for the current form state. */
    private void updatePricePreview() {
        try {
            int type = cmbTicketType.getSelectionModel().getSelectedIndex();
            int size = 1;
            if (type == Ticket.TYPE_GROUP) {
                for (String entry : txtGroupMembers.getText().split(";")) {
                    if (!entry.trim().isEmpty()) {
                        size++;
                    }
                }
            }
            double cost = PricingService.calculatePrice(type, parkState.getCurrentPeriod(), size);
            lblPrice.setText("Estimated Price: $" + String.format("%.2f", cost)
                             + " (" + size + (size == 1 ? " person)" : " people)"));
        } catch (RuntimeException e) {
            lblPrice.setText("Estimated Price: --");
        }
    }

    /** Validates the form and sells a ticket, mapping each refusal to a dialog. */
    private void handleBooking() {
        String name = txtName.getText().trim();
        String ageText = txtAge.getText().trim();
        String heightText = txtHeight.getText().trim();
        String rideName = cmbRide.getSelectionModel().getSelectedItem();

        if (name.isEmpty() || ageText.isEmpty() || heightText.isEmpty() || rideName == null) {
            Dialogs.error("Please fill in every visitor field.", "Validation Error");
            return;
        }

        int age;
        double height;
        try {
            age = Integer.parseInt(ageText);
            height = Double.parseDouble(heightText);
            if (age < 0 || height <= 0.0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            Dialogs.error("Age must be a whole number and height a positive decimal.",
                          "Validation Error");
            return;
        }

        int ticketType = cmbTicketType.getSelectionModel().getSelectedIndex();
        List<GroupMember> members = null;
        if (ticketType == Ticket.TYPE_GROUP) {
            members = new ArrayList<>();
            try {
                for (String entry : txtGroupMembers.getText().split(";")) {
                    GroupMember m = GroupMember.parse(entry);
                    if (m != null) {
                        members.add(m);
                    }
                }
            } catch (IllegalArgumentException e) {
                Dialogs.error(e.getMessage(), "Validation Error");
                return;
            }
            if (members.isEmpty()) {
                Dialogs.error("A group ticket needs at least one member besides the buyer.",
                              "Validation Error");
                return;
            }
        }

        Ride ride = parkState.findRideByName(rideName);

        try {
            Ticket issued = TicketingService.bookVisitor(
                parkState, name, age, height, ticketType, ride, members);

            Dialogs.info("Ticket " + issued.getTicketId() + "\n"
                         + issued.getTicketTypeString() + "\n"
                         + "Total charged: $" + String.format("%.2f", issued.getCurrentPrice()),
                         "Booking Confirmed");

            txtName.clear();
            txtAge.clear();
            txtHeight.clear();
            txtGroupMembers.clear();
            updatePricePreview();

        } catch (HeightRestrictionException e) {
            Dialogs.warn(e.getMessage(), "Safety Restriction");
        } catch (MaintenanceInProgressException e) {
            Dialogs.warn(e.getMessage(), "Ride Under Maintenance");
        } catch (RideClosedException e) {
            Dialogs.warn(e.getMessage(), "Ride Closed");
        } catch (CapacityExceededException e) {
            Dialogs.warn(e.getMessage(), "Queue Full");
        } catch (ParkException e) {
            Dialogs.warn(e.getMessage(), "Booking Refused");
        } catch (IllegalArgumentException e) {
            Dialogs.error("Invalid visitor details: " + e.getMessage(), "Validation Error");
        }
    }

    @Override
    public void refresh() {
        refreshRideCombo();
        updatePricePreview();
    }
}
