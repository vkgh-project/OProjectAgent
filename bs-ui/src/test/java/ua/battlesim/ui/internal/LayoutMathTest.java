package ua.battlesim.ui.internal;

import org.junit.jupiter.api.Test;
import ua.battlesim.ui.Rect;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LayoutMath} — verifies HUD layout calculations.
 */
class LayoutMathTest {

    @Test
    void mainPanel_position() {
        Rect r = LayoutMath.mainPanel(1080);
        assertEquals(0, r.x(), 0.001);
        assertEquals(0, r.y(), 0.001);
        assertEquals(LayoutMath.PANEL_WIDTH, r.width(), 0.001);
        assertEquals(1080, r.height(), 0.001);
    }

    @Test
    void mainPanel_invalidViewport() {
        assertThrows(IllegalArgumentException.class, () -> LayoutMath.mainPanel(0));
        assertThrows(IllegalArgumentException.class, () -> LayoutMath.mainPanel(-1));
    }

    @Test
    void markerRect_centeredAboveUnit() {
        // Unit at screen position (100, 200)
        Rect r = LayoutMath.markerRect(100, 200);
        // Marker should be 32x32 centered horizontally at x=100
        assertEquals(100, r.centerX(), 0.001);
        // Marker bottom should be MARKER_OFFSET pixels above the unit
        assertEquals(200 - LayoutMath.MARKER_OFFSET, r.bottom(), 0.001);
        // Marker size
        assertEquals(LayoutMath.MARKER_SIZE, r.width(), 0.001);
        assertEquals(LayoutMath.MARKER_SIZE, r.height(), 0.001);
    }

    @Test
    void markerRect_invalidPosition() {
        assertThrows(IllegalArgumentException.class,
                () -> LayoutMath.markerRect(Double.NaN, 100));
        assertThrows(IllegalArgumentException.class,
                () -> LayoutMath.markerRect(100, Double.POSITIVE_INFINITY));
    }

    @Test
    void bannerRect_centered() {
        int vw = 1920;
        Rect r = LayoutMath.bannerRect(vw);
        // Banner should be centered horizontally
        assertEquals(vw / 2.0, r.centerX(), 0.001);
        // Banner at top
        assertEquals(10, r.y(), 0.001);
        // Banner dimensions
        assertEquals(LayoutMath.BANNER_WIDTH, r.width(), 0.001);
        assertEquals(LayoutMath.BANNER_HEIGHT, r.height(), 0.001);
    }

    @Test
    void bannerRect_invalidViewport() {
        assertThrows(IllegalArgumentException.class, () -> LayoutMath.bannerRect(0));
    }

    @Test
    void keyHintBarRect_atBottom() {
        int vw = 1920, vh = 1080;
        Rect r = LayoutMath.keyHintBarRect(vw, vh);
        assertEquals(0, r.x(), 0.001);
        assertEquals(vh - LayoutMath.KEY_HINT_HEIGHT, r.y(), 0.001);
        assertEquals(vw, r.width(), 0.001);
        assertEquals(LayoutMath.KEY_HINT_HEIGHT, r.height(), 0.001);
    }

    @Test
    void panelTextLineRect_firstLine() {
        Rect r = LayoutMath.panelTextLineRect(0);
        assertEquals(10, r.x(), 0.001);
        assertEquals(10, r.y(), 0.001);
        assertEquals(LayoutMath.PANEL_WIDTH - 20, r.width(), 0.001);
        assertEquals(LayoutMath.LINE_SPACING, r.height(), 0.001);
    }

    @Test
    void panelTextLineRect_thirdLine() {
        Rect r = LayoutMath.panelTextLineRect(2);
        assertEquals(10, r.x(), 0.001);
        assertEquals(10 + 2 * LayoutMath.LINE_SPACING, r.y(), 0.001);
    }

    @Test
    void panelTextLineRect_invalidIndex() {
        assertThrows(IllegalArgumentException.class, () -> LayoutMath.panelTextLineRect(-1));
    }

    @Test
    void unitPanelRect_rightOfMainPanel() {
        int vh = 1080;
        Rect r = LayoutMath.unitPanelRect(vh);
        assertEquals(LayoutMath.PANEL_WIDTH + 10, r.x(), 0.001);
        assertEquals(0, r.y(), 0.001);
        assertEquals(LayoutMath.UNIT_PANEL_WIDTH, r.width(), 0.001);
        assertEquals(vh, r.height(), 0.001);
    }

    @Test
    void formatTime_zero() {
        assertEquals("00:00", LayoutMath.formatTime(0));
    }

    @Test
    void formatTime_30seconds() {
        assertEquals("00:30", LayoutMath.formatTime(30));
    }

    @Test
    void formatTime_30minutes() {
        assertEquals("30:00", LayoutMath.formatTime(1800));
    }

    @Test
    void formatTime_5min32sec() {
        assertEquals("05:32", LayoutMath.formatTime(332));
    }

    @Test
    void formatTime_invalid() {
        assertThrows(IllegalArgumentException.class, () -> LayoutMath.formatTime(-1));
        assertThrows(IllegalArgumentException.class, () -> LayoutMath.formatTime(Double.NaN));
    }
}
