package ua.battlesim.ui;

import ua.battlesim.api.Outcome;

/**
 * Round-result banner displayed at the top center of the screen.
 *
 * <p>Immutable and thread-safe.
 */
public record Banner(String text, Rect rect, Outcome outcome) {

    public Banner {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        if (rect == null) {
            throw new IllegalArgumentException("rect must not be null");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
    }
}
