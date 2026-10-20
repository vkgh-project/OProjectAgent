package ua.battlesim.gfx.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link UiCommand} records.
 */
class UiCommandTest {

    @Test
    void togglePause_canConstruct() {
        UiCommand cmd = new UiCommand.TogglePause();
        assertInstanceOf(UiCommand.TogglePause.class, cmd);
    }

    @Test
    void speedUp_canConstruct() {
        UiCommand cmd = new UiCommand.SpeedUp();
        assertInstanceOf(UiCommand.SpeedUp.class, cmd);
    }

    @Test
    void speedDown_canConstruct() {
        UiCommand cmd = new UiCommand.SpeedDown();
        assertInstanceOf(UiCommand.SpeedDown.class, cmd);
    }

    @Test
    void focusSelected_canConstruct() {
        UiCommand cmd = new UiCommand.FocusSelected();
        assertInstanceOf(UiCommand.FocusSelected.class, cmd);
    }

    @Test
    void resetCamera_canConstruct() {
        UiCommand cmd = new UiCommand.ResetCamera();
        assertInstanceOf(UiCommand.ResetCamera.class, cmd);
    }

    @Test
    void toggleFullscreen_canConstruct() {
        UiCommand cmd = new UiCommand.ToggleFullscreen();
        assertInstanceOf(UiCommand.ToggleFullscreen.class, cmd);
    }

    @Test
    void click_validCoordinates() {
        UiCommand cmd = new UiCommand.Click(100.0, 200.0);
        UiCommand.Click click = (UiCommand.Click) cmd;
        assertEquals(100.0, click.x());
        assertEquals(200.0, click.y());
    }

    @Test
    void click_nanX_throws() {
        assertThrows(IllegalArgumentException.class, () -> new UiCommand.Click(Double.NaN, 0.0));
    }

    @Test
    void click_infY_throws() {
        assertThrows(IllegalArgumentException.class, () -> new UiCommand.Click(0.0, Double.POSITIVE_INFINITY));
    }

    @Test
    void dragPan_validDeltas() {
        UiCommand cmd = new UiCommand.DragPan(10.0, -5.0);
        UiCommand.DragPan dp = (UiCommand.DragPan) cmd;
        assertEquals(10.0, dp.dx());
        assertEquals(-5.0, dp.dy());
    }

    @Test
    void dragPan_nanDx_throws() {
        assertThrows(IllegalArgumentException.class, () -> new UiCommand.DragPan(Double.NaN, 0.0));
    }

    @Test
    void dragRotate_validDeltas() {
        UiCommand cmd = new UiCommand.DragRotate(3.0, 7.0);
        UiCommand.DragRotate dr = (UiCommand.DragRotate) cmd;
        assertEquals(3.0, dr.dx());
        assertEquals(7.0, dr.dy());
    }

    @Test
    void dragRotate_infDy_throws() {
        assertThrows(IllegalArgumentException.class, () -> new UiCommand.DragRotate(0.0, Double.NEGATIVE_INFINITY));
    }

    @Test
    void zoom_validDelta() {
        UiCommand cmd = new UiCommand.Zoom(2.0);
        UiCommand.Zoom z = (UiCommand.Zoom) cmd;
        assertEquals(2.0, z.delta());
    }

    @Test
    void zoom_nanDelta_throws() {
        assertThrows(IllegalArgumentException.class, () -> new UiCommand.Zoom(Double.NaN));
    }

    @Test
    void sealedInterface_allSubtypes() {
        UiCommand a = new UiCommand.TogglePause();
        UiCommand b = new UiCommand.SpeedUp();
        UiCommand c = new UiCommand.SpeedDown();
        UiCommand d = new UiCommand.FocusSelected();
        UiCommand e = new UiCommand.ResetCamera();
        UiCommand f = new UiCommand.ToggleFullscreen();
        UiCommand g = new UiCommand.Click(0, 0);
        UiCommand h = new UiCommand.DragPan(0, 0);
        UiCommand i = new UiCommand.DragRotate(0, 0);
        UiCommand j = new UiCommand.Zoom(0);
        assertNotNull(a); assertNotNull(b); assertNotNull(c);
        assertNotNull(d); assertNotNull(e); assertNotNull(f);
        assertNotNull(g); assertNotNull(h); assertNotNull(i); assertNotNull(j);
    }
}
