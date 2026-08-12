import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * Live colour-coded event feed (FR-15).
 *
 * <p>Registers itself as an {@link AuditLogger.AuditLogListener}, which is the
 * Observer half of the logging design: simulation threads publish entries without
 * knowing the GUI exists, and {@code AuditLogger} marshals them onto the JavaFX
 * application thread before delivery.</p>
 */
public class EventLogPanel implements ParkPanel, AuditLogger.AuditLogListener {

    /** Entries retained before the oldest are dropped. */
    private static final int MAX_ENTRIES = 400;

    /** Each entry contributes three Text nodes: timestamp, category, message. */
    private static final int NODES_PER_ENTRY = 3;

    private final BorderPane root = new BorderPane();
    private final TextFlow flow = new TextFlow();
    private final ScrollPane scroll = new ScrollPane(flow);

    /** Builds the panel and subscribes to the audit log. */
    public EventLogPanel() {
        build();
        AuditLogger.getInstance().addListener(this);
    }

    @Override
    public String getTitle() {
        return "Live Log Feed";
    }

    @Override
    public Parent getView() {
        return root;
    }

    private void build() {
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: " + Theme.BG_DARK + ";");

        flow.setPadding(new Insets(12));
        flow.setStyle("-fx-background-color: #16181e;");
        flow.setLineSpacing(2);

        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #16181e; -fx-background-color: #16181e;"
                      + "-fx-border-color: " + Theme.BORDER + ";");
        root.setCenter(scroll);
    }

    /**
     * Maps a log category to its display colour.
     *
     * @param category the entry's source
     * @return the colour for that category
     */
    private Color colourFor(String category) {
        if (category.startsWith("RIDE-")) {
            return Color.web(Theme.ACCENT_BLUE_HOVER);
        }
        switch (category) {
            case "TICKETING":   return Color.web("#52c86e");
            case "MAINTENANCE": return Color.web(Theme.ACCENT_ORANGE);
            case "MANAGER":     return Color.web("#d98cff");
            case "CLOCK":       return Color.web("#5ad4d4");
            case "ERROR":       return Color.web(Theme.ACCENT_RED);
            case "WARNING":     return Color.web("#ffd35c");
            default:            return Color.web(Theme.TEXT_MUTED);
        }
    }

    @Override
    public void onLogAdded(String category, String message, String timestamp) {
        Text time = new Text(timestamp + "  ");
        time.setFill(Color.web(Theme.TEXT_MUTED));
        time.setFont(Theme.font(12, false));

        Text cat = new Text("[" + category + "] ");
        cat.setFill(colourFor(category));
        cat.setFont(Theme.font(12, true));

        Text body = new Text(message + "\n");
        body.setFill(Color.web(Theme.TEXT_LIGHT));
        body.setFont(Theme.font(12, false));

        flow.getChildren().addAll(time, cat, body);

        // Trim from the front so a long simulation cannot grow the scene graph
        // without bound.
        int excess = flow.getChildren().size() - (MAX_ENTRIES * NODES_PER_ENTRY);
        if (excess > 0) {
            flow.getChildren().remove(0, excess);
        }

        scroll.setVvalue(1.0);
    }

    @Override
    public void refresh() {
        // Push-driven through the observer, so there is nothing to poll.
    }

    /** Unsubscribes from the audit log. Called when the window closes. */
    public void dispose() {
        AuditLogger.getInstance().removeListener(this);
    }
}
