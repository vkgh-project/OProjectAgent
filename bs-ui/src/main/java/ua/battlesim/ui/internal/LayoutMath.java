package ua.battlesim.ui.internal;

import ua.battlesim.ui.Rect;

/**
 * Layout calculations for the HUD elements.
 *
 * <p>All positions are in screen pixel coordinates (top-left origin).
 *
 * <p>Immutable; all methods are static.
 */
public final class LayoutMath {

    /** Width of the main info panel in pixels. */
    public static final int PANEL_WIDTH = 250;

    /** Width of the unit detail panel in pixels. */
    public static final int UNIT_PANEL_WIDTH = 250;

    /** Size of NATO markers in pixels (square). */
    public static final int MARKER_SIZE = 32;

    /** Offset above the unit screen position for the NATO marker, in pixels. */
    public static final int MARKER_OFFSET = 24;

    /** Font size for panel text in pixels. */
    public static final int FONT_SIZE = 14;

    /** Font size for the banner in pixels. */
    public static final int BANNER_FONT_SIZE = 24;

    /** Banner height in pixels. */
    public static final int BANNER_HEIGHT = 50;

    /** Banner width in pixels. */
    public static final int BANNER_WIDTH = 400;

    /** Key hint bar height in pixels. */
    public static final int KEY_HINT_HEIGHT = 30;

    /** Vertical spacing between text lines in pixels. */
    public static final int LINE_SPACING = 24;

    /** Maximum simulated time in seconds (30 minutes). */
    public static final double MAX_TIME_SECONDS = 1800.0;

    private LayoutMath() {}

    /**
     * Computes the rect for the main info panel (left side of viewport).
     *
     * @param viewportHeight viewport height in pixels
     * @return the panel rect
     * @throws IllegalArgumentException if viewportHeight is not positive
     */
    public static Rect mainPanel(int viewportHeight) {
        if (viewportHeight <= 0) {
            throw new IllegalArgumentException("viewportHeight must be > 0: " + viewportHeight);
        }
        return new Rect(0, 0, PANEL_WIDTH, viewportHeight);
    }

    /**
     * Computes the rect for the NATO marker above a unit at screen position (sx, sy).
     *
     * @param sx unit screen x in pixels
     * @param sy unit screen y in pixels
     * @return the marker rect centered above the unit position
     * @throws IllegalArgumentException if sx or sy is not finite
     */
    public static Rect markerRect(double sx, double sy) {
        if (!Double.isFinite(sx) || !Double.isFinite(sy)) {
            throw new IllegalArgumentException("screen position must be finite: " + sx + ", " + sy);
        }
        double x = sx - MARKER_SIZE / 2.0;
        double y = sy - MARKER_OFFSET - MARKER_SIZE;
        return new Rect(x, y, MARKER_SIZE, MARKER_SIZE);
    }

    /**
     * Computes the rect for the banner at the top center of the viewport.
     *
     * @param viewportWidth viewport width in pixels
     * @return the banner rect
     * @throws IllegalArgumentException if viewportWidth is not positive
     */
    public static Rect bannerRect(int viewportWidth) {
        if (viewportWidth <= 0) {
            throw new IllegalArgumentException("viewportWidth must be > 0: " + viewportWidth);
        }
        double x = (viewportWidth - BANNER_WIDTH) / 2.0;
        return new Rect(x, 10, BANNER_WIDTH, BANNER_HEIGHT);
    }

    /**
     * Computes the rect for the key hint bar at the bottom of the viewport.
     *
     * @param viewportWidth viewport width in pixels
     * @param viewportHeight viewport height in pixels
     * @return the key hint bar rect
     * @throws IllegalArgumentException if viewport dimensions are not positive
     */
    public static Rect keyHintBarRect(int viewportWidth, int viewportHeight) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("viewport must be > 0: " + viewportWidth + ", " + viewportHeight);
        }
        return new Rect(0, viewportHeight - KEY_HINT_HEIGHT, viewportWidth, KEY_HINT_HEIGHT);
    }

    /**
     * Computes the rect for the i-th text line in the main panel.
     *
     * @param i the line index (0-based, non-negative)
     * @return the rect for this text line
     * @throws IllegalArgumentException if i is negative
     */
    public static Rect panelTextLineRect(int i) {
        if (i < 0) {
            throw new IllegalArgumentException("line index must be >= 0: " + i);
        }
        return new Rect(10, 10 + i * (double) LINE_SPACING, PANEL_WIDTH - 20, LINE_SPACING);
    }

    /**
     * Computes the rect for the unit detail panel (right of the main panel).
     *
     * @param viewportHeight viewport height in pixels
     * @return the unit panel rect
     * @throws IllegalArgumentException if viewportHeight is not positive
     */
    public static Rect unitPanelRect(int viewportHeight) {
        if (viewportHeight <= 0) {
            throw new IllegalArgumentException("viewportHeight must be > 0: " + viewportHeight);
        }
        return new Rect(PANEL_WIDTH + 10, 0, UNIT_PANEL_WIDTH, viewportHeight);
    }

    /**
     * Formats a time in seconds as MM:SS.
     *
     * @param seconds time in seconds, non-negative
     * @return formatted time string
     * @throws IllegalArgumentException if seconds is not finite or negative
     */
    public static String formatTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0) {
            throw new IllegalArgumentException("seconds must be finite and >= 0: " + seconds);
        }
        int totalSeconds = (int) seconds;
        int minutes = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }
}
