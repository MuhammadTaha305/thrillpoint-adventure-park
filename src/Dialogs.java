import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;

import java.util.List;
import java.util.Optional;

/**
 * Themed dialog helpers.
 *
 * <p>JavaFX dialogs open in their own window and do not inherit the main scene's
 * styling, so each one is passed through {@link Theme#styleDialog} before it is
 * shown. Centralising that here keeps the six panels from repeating it.</p>
 */
public final class Dialogs {

    private Dialogs() {
        // static helper class, never instantiated
    }

    /**
     * @param alert the dialog to prepare
     * @param title window and header text
     * @param body  the message
     */
    private static void prepare(Alert alert, String title, String body) {
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(body);
        alert.getDialogPane().setMinWidth(420);
        Theme.styleDialog(alert.getDialogPane());
    }

    /**
     * @param body  the message
     * @param title window title
     */
    public static void info(String body, String title) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        prepare(a, title, body);
        a.showAndWait();
    }

    /**
     * @param body  the message
     * @param title window title
     */
    public static void warn(String body, String title) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        prepare(a, title, body);
        a.showAndWait();
    }

    /**
     * @param body  the message
     * @param title window title
     */
    public static void error(String body, String title) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        prepare(a, title, body);
        a.showAndWait();
    }

    /**
     * @param body  the question
     * @param title window title
     * @return {@code true} when the user chose OK
     */
    public static boolean confirm(String body, String title) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        prepare(a, title, body);
        Optional<ButtonType> answer = a.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }

    /**
     * Asks the user to pick one item from a list.
     *
     * @param body    the prompt
     * @param title   window title
     * @param choices the options; must not be empty
     * @return the chosen item, or {@code null} if cancelled
     */
    public static String choose(String body, String title, List<String> choices) {
        if (choices.isEmpty()) {
            return null;
        }
        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.get(0), choices);
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.setContentText(body);
        dialog.getDialogPane().setMinWidth(420);
        Theme.styleDialog(dialog.getDialogPane());
        Optional<String> answer = dialog.showAndWait();
        return answer.orElse(null);
    }
}
