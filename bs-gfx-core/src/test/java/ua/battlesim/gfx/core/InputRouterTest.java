package ua.battlesim.gfx.core;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InputRouter} (no GPU required).
 */
class InputRouterTest {

    private static final int PRESS = 1;
    private static final int RELEASE = 0;

    @Test
    void key_space_producesTogglePause() {
        InputRouter router = new InputRouter();
        Optional<UiCommand> cmd = router.onKey(32, PRESS); // SPACE
        assertTrue(cmd.isPresent());
        assertInstanceOf(UiCommand.TogglePause.class, cmd.get());
    }

    @Test
    void key_equal_producesSpeedUp() {
        InputRouter router = new InputRouter();
        Optional<UiCommand> cmd = router.onKey(61, PRESS); // =
        assertTrue(cmd.isPresent());
        assertInstanceOf(UiCommand.SpeedUp.class, cmd.get());
    }

    @Test
    void key_minus_producesSpeedDown() {
        InputRouter router = new InputRouter();
        Optional<UiCommand> cmd = router.onKey(45, PRESS); // -
        assertTrue(cmd.isPresent());
        assertInstanceOf(UiCommand.SpeedDown.class, cmd.get());
    }

    @Test
    void key_f1_producesFocusSelected() {
        InputRouter router = new InputRouter();
        Optional<UiCommand> cmd = router.onKey(290, PRESS); // F1
        assertTrue(cmd.isPresent());
        assertInstanceOf(UiCommand.FocusSelected.class, cmd.get());
    }

    @Test
    void key_r_producesResetCamera() {
        InputRouter router = new InputRouter();
        Optional<UiCommand> cmd = router.onKey(82, PRESS); // R
        assertTrue(cmd.isPresent());
        assertInstanceOf(UiCommand.ResetCamera.class, cmd.get());
    }

    @Test
    void key_f11_producesToggleFullscreen() {
        InputRouter router = new InputRouter();
        Optional<UiCommand> cmd = router.onKey(302, PRESS); // F11
        assertTrue(cmd.isPresent());
        assertInstanceOf(UiCommand.ToggleFullscreen.class, cmd.get());
    }

    @Test
    void key_unmapped_producesEmpty() {
        InputRouter router = new InputRouter();
        Optional<UiCommand> cmd = router.onKey(97, PRESS); // A
        assertTrue(!cmd.isPresent());
    }

    @Test
    void key_release_producesEmpty() {
        InputRouter router = new InputRouter();
        Optional<UiCommand> cmd = router.onKey(32, RELEASE); // SPACE release
        assertTrue(!cmd.isPresent());
    }

    @Test
    void mouseLeft_click_producesClick() {
        InputRouter router = new InputRouter();
        router.onMouseButton(0, PRESS, 100, 200); // left press
        Optional<UiCommand> cmd = router.onMouseButton(0, RELEASE, 100, 200); // left release (no move)
        assertTrue(cmd.isPresent());
        assertInstanceOf(UiCommand.Click.class, cmd.get());
        UiCommand.Click click = (UiCommand.Click) cmd.get();
        assertEquals(100.0, click.x());
        assertEquals(200.0, click.y());
    }

    @Test
    void mouseLeft_drag_producesDragPan() {
        InputRouter router = new InputRouter();
        router.onMouseButton(0, PRESS, 100, 100); // left press
        router.onMouseMove(101, 100); // tiny move (below threshold)
        Optional<UiCommand> cmd = router.onMouseMove(110, 100); // move past threshold
        assertTrue(cmd.isPresent());
        assertInstanceOf(UiCommand.DragPan.class, cmd.get());
    }

    @Test
    void mouseRight_drag_producesDragRotate() {
        InputRouter router = new InputRouter();
        router.onMouseButton(1, PRESS, 100, 100); // right press
        router.onMouseMove(101, 100); // tiny move
        Optional<UiCommand> cmd = router.onMouseMove(115, 100); // move past threshold
        assertTrue(cmd.isPresent());
        assertInstanceOf(UiCommand.DragRotate.class, cmd.get());
    }

    @Test
    void mouseLeft_dragThenRelease_noClick() {
        InputRouter router = new InputRouter();
        router.onMouseButton(0, PRESS, 100, 100);
        router.onMouseMove(101, 100);
        router.onMouseMove(120, 100); // drag
        Optional<UiCommand> cmd = router.onMouseButton(0, RELEASE, 120, 100);
        // After a drag, release should NOT produce a Click
        assertTrue(!cmd.isPresent());
    }

    @Test
    void scroll_producesZoom() {
        InputRouter router = new InputRouter();
        Optional<UiCommand> cmd = router.onScroll(0.0, 3.0);
        assertTrue(cmd.isPresent());
        assertInstanceOf(UiCommand.Zoom.class, cmd.get());
        assertEquals(3.0, ((UiCommand.Zoom) cmd.get()).delta());
    }

    @Test
    void scroll_zero_producesEmpty() {
        InputRouter router = new InputRouter();
        Optional<UiCommand> cmd = router.onScroll(0.0, 0.0);
        assertTrue(!cmd.isPresent());
    }

    @Test
    void mouseMove_noButton_producesEmpty() {
        InputRouter router = new InputRouter();
        Optional<UiCommand> cmd = router.onMouseMove(100, 100);
        assertTrue(!cmd.isPresent());
    }

    @Test
    void dragThreshold_respected() {
        InputRouter router = new InputRouter();
        router.onMouseButton(0, PRESS, 100, 100);
        // Move 3 pixels (below threshold of 4)
        Optional<UiCommand> cmd = router.onMouseMove(103, 100);
        assertTrue(!cmd.isPresent());
        // Move 5 more pixels (total 8, above threshold)
        Optional<UiCommand> cmd2 = router.onMouseMove(108, 100);
        assertTrue(cmd2.isPresent());
    }
}
