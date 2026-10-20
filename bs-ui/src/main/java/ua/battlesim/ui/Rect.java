package ua.battlesim.ui;

import static java.lang.Double.isFinite;

/**
 * A rectangle in screen pixel coordinates (top-left origin).
 *
 * <p>x increases to the right, y increases downward.
 *
 * <p>Immutable and thread-safe.
 */
public record Rect(double x, double y, double width, double height) {

    public Rect {
        for (double v : new double[]{x, y, width, height}) {
            if (!isFinite(v)) {
                throw new IllegalArgumentException("Rect values must be finite, got: " + v);
            }
        }
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("width and height must be >= 0: " + width + ", " + height);
        }
    }

    /** @return true if point (px, py) is inside this rectangle (inclusive left/top, exclusive right/bottom) */
    public boolean contains(double px, double py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    /** @return the right edge x coordinate */
    public double right() { return x + width; }

    /** @return the bottom edge y coordinate */
    public double bottom() { return y + height; }

    /** @return the center x coordinate */
    public double centerX() { return x + width / 2.0; }

    /** @return the center y coordinate */
    public double centerY() { return y + height / 2.0; }
}
