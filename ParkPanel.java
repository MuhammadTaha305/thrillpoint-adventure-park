import javafx.scene.Parent;

/**
 * Contract every tab in the dashboard implements.
 *
 * <p>This is what makes the interface splittable: {@link MainWindow} holds a list
 * of {@code ParkPanel} and knows nothing about any individual tab beyond these
 * three methods. Each panel can be written, reviewed and owned independently, and
 * adding a seventh tab means adding one class and one line.</p>
 */
public interface ParkPanel {

    /** @return the tab caption */
    String getTitle();

    /** @return the root node of this tab's content */
    Parent getView();

    /**
     * Re-reads the park and updates the display.
     *
     * <p>Always called on the JavaFX application thread by the shell's refresh
     * timeline, so implementations may touch controls directly.</p>
     */
    void refresh();
}
