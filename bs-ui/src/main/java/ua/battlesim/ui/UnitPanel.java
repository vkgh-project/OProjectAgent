package ua.battlesim.ui;

/**
 * The selected unit information panel in the HUD.
 *
 * <p>Displays: id, side, type, coordinates, compass heading, health bar, state, target.
 *
 * <p>Immutable and thread-safe.
 */
public record UnitPanel(int id, String sideLabel, String typeLabel, String coordinates,
                        String compassHeading, HealthBar healthBar, String stateLabel,
                        String targetLabel, Rect rect) {

    public UnitPanel {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0: " + id);
        }
        if (sideLabel == null) {
            throw new IllegalArgumentException("sideLabel must not be null");
        }
        if (typeLabel == null) {
            throw new IllegalArgumentException("typeLabel must not be null");
        }
        if (coordinates == null) {
            throw new IllegalArgumentException("coordinates must not be null");
        }
        if (compassHeading == null) {
            throw new IllegalArgumentException("compassHeading must not be null");
        }
        if (healthBar == null) {
            throw new IllegalArgumentException("healthBar must not be null");
        }
        if (stateLabel == null) {
            throw new IllegalArgumentException("stateLabel must not be null");
        }
        if (rect == null) {
            throw new IllegalArgumentException("rect must not be null");
        }
    }
}
