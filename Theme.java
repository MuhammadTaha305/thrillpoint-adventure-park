import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Shared colours and styled-control factories for the JavaFX interface.
 *
 * <p>Styling is applied programmatically with {@code setStyle} rather than an
 * external stylesheet. A {@code .css} file would be a classpath resource, which
 * would have to be copied into {@code bin/} as a separate build step — this way
 * {@code javac -d bin src/*.java} remains the whole build.</p>
 *
 * <p>Every panel builds its controls through this class, so the six tabs stay
 * visually consistent even though three people are writing them.</p>
 */
public final class Theme {

    /** Window and tab background. */
    public static final String BG_DARK = "#1c1e26";

    /** Raised card background. */
    public static final String BG_CARD = "#262933";

    /** Input control background. */
    public static final String BG_INPUT = "#30343f";

    /** Primary accent. */
    public static final String ACCENT_BLUE = "#528bff";

    /** Primary accent, hover state. */
    public static final String ACCENT_BLUE_HOVER = "#6ea0ff";

    /** Positive / success accent. */
    public static final String ACCENT_GREEN = "#28a745";

    /** Destructive accent. */
    public static final String ACCENT_RED = "#dc3545";

    /** Warning accent. */
    public static final String ACCENT_ORANGE = "#fd7e14";

    /** Primary text colour. */
    public static final String TEXT_LIGHT = "#dcdfe4";

    /** Secondary text colour. */
    public static final String TEXT_MUTED = "#9196a5";

    /** Hairline border colour. */
    public static final String BORDER = "#373c4b";

    /** UI font family. */
    public static final String FONT = "Segoe UI";

    private Theme() {
        // static factory class, never instantiated
    }

    /**
     * @param size  point size
     * @param bold  {@code true} for a bold face
     * @return a font in the interface family
     */
    public static Font font(double size, boolean bold) {
        return bold ? Font.font(FONT, FontWeight.BOLD, size) : Font.font(FONT, size);
    }

    /**
     * @param text the caption
     * @return a bold form label in the primary text colour
     */
    public static Label formLabel(String text) {
        Label l = new Label(text);
        l.setFont(font(13, true));
        l.setStyle("-fx-text-fill: " + TEXT_LIGHT + ";");
        return l;
    }

    /**
     * @param text the caption
     * @return a small muted label for hints and secondary information
     */
    public static Label mutedLabel(String text) {
        Label l = new Label(text);
        l.setFont(font(11, false));
        l.setStyle("-fx-text-fill: " + TEXT_MUTED + ";");
        return l;
    }

    /**
     * @param text the caption
     * @return a section heading
     */
    public static Label heading(String text) {
        Label l = new Label(text);
        l.setFont(font(15, true));
        l.setStyle("-fx-text-fill: " + TEXT_LIGHT + ";");
        return l;
    }

    /**
     * @return an empty dark-themed text field
     */
    public static TextField textField() {
        TextField f = new TextField();
        f.setFont(font(13, false));
        f.setStyle("-fx-background-color: " + BG_INPUT + ";"
                 + "-fx-text-fill: white;"
                 + "-fx-prompt-text-fill: " + TEXT_MUTED + ";"
                 + "-fx-border-color: " + BORDER + ";"
                 + "-fx-border-radius: 3; -fx-background-radius: 3;"
                 + "-fx-padding: 6 8 6 8;");
        return f;
    }

    /**
     * @param rows visible row count
     * @return a dark-themed multi-line text area
     */
    public static TextArea textArea(int rows) {
        TextArea a = new TextArea();
        a.setPrefRowCount(rows);
        a.setWrapText(true);
        a.setFont(font(13, false));
        a.setStyle("-fx-control-inner-background: " + BG_INPUT + ";"
                 + "-fx-text-fill: white;"
                 + "-fx-highlight-fill: " + ACCENT_BLUE + ";"
                 + "-fx-border-color: " + BORDER + ";"
                 + "-fx-background-color: " + BORDER + ";");
        return a;
    }

    /**
     * Applies the dark theme to a combo box.
     *
     * @param box the control to style
     * @param <T> the combo box item type
     * @return the same control, for chaining
     */
    public static <T> ComboBox<T> style(ComboBox<T> box) {
        box.setStyle("-fx-background-color: " + BG_INPUT + ";"
                   + "-fx-mark-color: " + TEXT_LIGHT + ";"
                   + "-fx-border-color: " + BORDER + ";"
                   + "-fx-border-radius: 3; -fx-background-radius: 3;");
        box.setButtonCell(new javafx.scene.control.ListCell<T>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.toString());
                setStyle("-fx-text-fill: " + TEXT_LIGHT + "; -fx-font-size: 13;");
            }
        });
        return box;
    }

    /**
     * @param text  the caption
     * @param base  the resting background colour
     * @param hover the hover background colour
     * @return a flat coloured button with a hover transition
     */
    public static Button button(String text, String base, String hover) {
        Button b = new Button(text);
        b.setFont(font(13, true));
        String common = "-fx-text-fill: white; -fx-background-radius: 4;"
                      + "-fx-padding: 8 16 8 16; -fx-cursor: hand;";
        b.setStyle("-fx-background-color: " + base + ";" + common);
        b.setOnMouseEntered(e -> b.setStyle("-fx-background-color: " + hover + ";" + common));
        b.setOnMouseExited(e -> b.setStyle("-fx-background-color: " + base + ";" + common));
        return b;
    }

    /**
     * @param text the caption
     * @return a button in the primary accent colour
     */
    public static Button primaryButton(String text) {
        return button(text, ACCENT_BLUE, ACCENT_BLUE_HOVER);
    }

    /**
     * @return a card container with the raised background and a hairline border
     */
    public static VBox card() {
        VBox v = new VBox(10);
        v.setStyle("-fx-background-color: " + BG_CARD + ";"
                 + "-fx-border-color: " + BORDER + ";"
                 + "-fx-border-radius: 4; -fx-background-radius: 4;"
                 + "-fx-padding: 16;");
        return v;
    }

    /**
     * @param title the card's heading
     * @return a card with a heading already in place
     */
    public static VBox card(String title) {
        VBox v = card();
        v.getChildren().add(heading(title));
        return v;
    }

    /**
     * Applies the dark theme to a table.
     *
     * @param region the table (or any region) to style
     */
    public static void styleTable(Region region) {
        region.setStyle("-fx-background-color: " + BG_CARD + ";"
                      + "-fx-control-inner-background: " + BG_CARD + ";"
                      + "-fx-control-inner-background-alt: " + BG_CARD + ";"
                      + "-fx-table-cell-border-color: " + BORDER + ";"
                      + "-fx-text-background-color: " + TEXT_LIGHT + ";"
                      + "-fx-border-color: " + BORDER + ";"
                      + "-fx-selection-bar: " + ACCENT_BLUE + ";"
                      + "-fx-selection-bar-non-focused: " + BORDER + ";");
    }

    /**
     * Applies the dark theme to a dialog, which is a separate window and so does
     * not inherit the main scene's styling.
     *
     * @param pane the dialog pane to style
     */
    public static void styleDialog(DialogPane pane) {
        pane.setStyle("-fx-background-color: " + BG_CARD + ";");
        pane.lookupAll(".label").forEach(n ->
            n.setStyle("-fx-text-fill: " + TEXT_LIGHT + "; -fx-font-size: 13;"));
    }
}
