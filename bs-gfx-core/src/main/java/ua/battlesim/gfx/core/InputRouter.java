package ua.battlesim.gfx.core;

import java.util.Optional;

/**
 * Routes raw input events to {@link UiCommand} records.
 *
 * <p>Stateful: tracks mouse button state and drag position to distinguish
 * between clicks and drags.
 *
 * <p>Key bindings:
 * <ul>
 *   <li>SPACE &rarr; {@link UiCommand.TogglePause}</li>
 *   <li>= / KP_PLUS &rarr; {@link UiCommand.SpeedUp}</li>
 *   <li>- / KP_MINUS &rarr; {@link UiCommand.SpeedDown}</li>
 *   <li>F1 &rarr; {@link UiCommand.FocusSelected}</li>
 *   <li>R &rarr; {@link UiCommand.ResetCamera}</li>
 *   <li>F11 &rarr; {@link UiCommand.ToggleFullscreen}</li>
 *   <li>Left mouse click &rarr; {@link UiCommand.Click}</li>
 *   <li>Left mouse drag &rarr; {@link UiCommand.DragPan}</li>
 *   <li>Right mouse drag &rarr; {@link UiCommand.DragRotate}</li>
 *   <li>Scroll wheel &rarr; {@link UiCommand.Zoom}</li>
 * </ul>
 *
 * <p>Thread-safety: not thread-safe; call from a single thread.
 */
public final class InputRouter {

    /** Pixel distance below which a mouse release is considered a click. */
    public static final double DRAG_THRESHOLD = 4.0;

    // GLFW-compatible key constants
    private static final int KEY_SPACE = 32;
    private static final int KEY_MINUS = 45;
    private static final int KEY_EQUAL = 61;
    private static final int KEY_R = 82;
    private static final int KEY_KP_PLUS = 107;
    private static final int KEY_KP_MINUS = 109;
    private static final int KEY_F1 = 290;
    private static final int KEY_F11 = 302;

    // GLFW-compatible mouse button constants
    private static final int BUTTON_LEFT = 0;
    private static final int BUTTON_RIGHT = 1;

    // GLFW-compatible action constants
    private static final int ACTION_RELEASE = 0;
    private static final int ACTION_PRESS = 1;

    private double pressX, pressY;
    private double lastX, lastY;
    private int activeButton = -1;
    private boolean dragging = false;

    /** Creates a new input router in its initial state. */
    public InputRouter() {}

    /**
     * Routes a key event to a {@link UiCommand}.
     *
     * @param key    the key code (GLFW key constant)
     * @param action the action (0 = release, 1 = press, 2 = repeat)
     * @return the command, or empty if no command maps to this event
     */
    public Optional<UiCommand> onKey(int key, int action) {
        if (action != ACTION_PRESS) return Optional.empty();
        return switch (key) {
            case KEY_SPACE -> Optional.of(new UiCommand.TogglePause());
            case KEY_EQUAL, KEY_KP_PLUS -> Optional.of(new UiCommand.SpeedUp());
            case KEY_MINUS, KEY_KP_MINUS -> Optional.of(new UiCommand.SpeedDown());
            case KEY_F1 -> Optional.of(new UiCommand.FocusSelected());
            case KEY_R -> Optional.of(new UiCommand.ResetCamera());
            case KEY_F11 -> Optional.of(new UiCommand.ToggleFullscreen());
            default -> Optional.empty();
        };
    }

    /**
     * Routes a mouse button event.
     *
     * @param button the button (0 = left, 1 = right, 2 = middle)
     * @param action the action (0 = release, 1 = press, 2 = repeat)
     * @param x      cursor x in pixels
     * @param y      cursor y in pixels
     * @return a {@link UiCommand.Click} on release (if not dragged), empty otherwise
     */
    public Optional<UiCommand> onMouseButton(int button, int action, double x, double y) {
        if (action == ACTION_PRESS) {
            pressX = x;
            pressY = y;
            lastX = x;
            lastY = y;
            dragging = false;
            if (button == BUTTON_LEFT || button == BUTTON_RIGHT) {
                activeButton = button;
            }
            return Optional.empty();
        }
        if (action == ACTION_RELEASE) {
            if (button == activeButton) {
                activeButton = -1;
                if (!dragging) {
                    return Optional.of(new UiCommand.Click(x, y));
                }
            }
            dragging = false;
            return Optional.empty();
        }
        return Optional.empty();
    }

    /**
     * Routes a mouse move event.
     *
     * @param x cursor x in pixels
     * @param y cursor y in pixels
     * @return a {@link UiCommand.DragPan} or {@link UiCommand.DragRotate} if dragging, empty otherwise
     */
    public Optional<UiCommand> onMouseMove(double x, double y) {
        if (activeButton == -1) return Optional.empty();

        double dx = x - lastX;
        double dy = y - lastY;

        if (!dragging) {
            double dist = StrictMath.sqrt(dx * dx + dy * dy);
            if (dist < DRAG_THRESHOLD) return Optional.empty();
            dragging = true;
        }

        lastX = x;
        lastY = y;

        if (activeButton == BUTTON_LEFT) {
            return Optional.of(new UiCommand.DragPan(dx, dy));
        } else if (activeButton == BUTTON_RIGHT) {
            return Optional.of(new UiCommand.DragRotate(dx, dy));
        }
        return Optional.empty();
    }

    /**
     * Routes a scroll event.
     *
     * @param xOffset horizontal scroll delta
     * @param yOffset vertical scroll delta (positive = scroll up / zoom in)
     * @return a {@link UiCommand.Zoom}, or empty if no scroll occurred
     */
    public Optional<UiCommand> onScroll(double xOffset, double yOffset) {
        if (xOffset == 0.0 && yOffset == 0.0) return Optional.empty();
        return Optional.of(new UiCommand.Zoom(yOffset));
    }
}