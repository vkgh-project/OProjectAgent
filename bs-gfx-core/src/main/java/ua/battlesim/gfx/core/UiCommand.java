package ua.battlesim.gfx.core;

/**
 * UI commands produced by the {@link InputRouter} and consumed by the
 * presentation layer.
 *
 * <p>Immutable and thread-safe.
 */
public sealed interface UiCommand
        permits UiCommand.TogglePause, UiCommand.SpeedUp, UiCommand.SpeedDown,
               UiCommand.FocusSelected, UiCommand.ResetCamera, UiCommand.ToggleFullscreen,
               UiCommand.Click, UiCommand.DragPan, UiCommand.DragRotate, UiCommand.Zoom {

    /** Toggles pause/resume of the simulation. */
    record TogglePause() implements UiCommand {}

    /** Increases the playback speed. */
    record SpeedUp() implements UiCommand {}

    /** Decreases the playback speed. */
    record SpeedDown() implements UiCommand {}

    /** Focuses the camera on the selected unit. */
    record FocusSelected() implements UiCommand {}

    /** Resets the camera to its default position. */
    record ResetCamera() implements UiCommand {}

    /** Toggles fullscreen mode. */
    record ToggleFullscreen() implements UiCommand {}

    /** A single click at the given screen position in pixels. */
    record Click(double x, double y) implements UiCommand {
        public Click {
            if (!Double.isFinite(x) || !Double.isFinite(y))
                throw new IllegalArgumentException("coordinates must be finite: " + x + ", " + y);
        }
    }

    /** A drag-pan with the given pixel deltas. */
    record DragPan(double dx, double dy) implements UiCommand {
        public DragPan {
            if (!Double.isFinite(dx) || !Double.isFinite(dy))
                throw new IllegalArgumentException("deltas must be finite: " + dx + ", " + dy);
        }
    }

    /** A drag-rotate with the given pixel deltas. */
    record DragRotate(double dx, double dy) implements UiCommand {
        public DragRotate {
            if (!Double.isFinite(dx) || !Double.isFinite(dy))
                throw new IllegalArgumentException("deltas must be finite: " + dx + ", " + dy);
        }
    }

    /** A zoom with the given delta (positive = zoom in). */
    record Zoom(double delta) implements UiCommand {
        public Zoom {
            if (!Double.isFinite(delta))
                throw new IllegalArgumentException("delta must be finite: " + delta);
        }
    }
}