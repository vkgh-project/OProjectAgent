package ua.battlesim.ui;

/**
 * A text element in the HUD with position and styling.
 *
 * <p>{@code colorArgb} is a 32-bit ARGB color (0xAARRGGBB).
 *
 * <p>Immutable and thread-safe.
 */
public record HudText(String text, Rect rect, int fontSize, int colorArgb) {

    public HudText {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        if (rect == null) {
            throw new IllegalArgumentException("rect must not be null");
        }
        if (fontSize < 1) {
            throw new IllegalArgumentException("fontSize must be >= 1: " + fontSize);
        }
    }
}
