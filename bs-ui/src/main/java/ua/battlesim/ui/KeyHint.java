package ua.battlesim.ui;

/**
 * A keyboard/mouse key hint displayed in the HUD key-hint bar.
 *
 * <p>Immutable and thread-safe.
 */
public record KeyHint(String key, String description, Rect rect) {

    public KeyHint {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (description == null) {
            throw new IllegalArgumentException("description must not be null");
        }
        if (rect == null) {
            throw new IllegalArgumentException("rect must not be null");
        }
    }
}
