package ua.battlesim.ui;

/**
 * Health bar representation in the selected unit panel.
 *
 * <p>Immutable and thread-safe.
 */
public record HealthBar(Rect rect, int health, int maxHealth) {

    public HealthBar {
        if (rect == null) {
            throw new IllegalArgumentException("rect must not be null");
        }
        if (health < 0 || health > 100) {
            throw new IllegalArgumentException("health must be in [0,100]: " + health);
        }
        if (maxHealth < 1) {
            throw new IllegalArgumentException("maxHealth must be >= 1: " + maxHealth);
        }
    }

    /** @return the fill fraction in [0, 1] */
    public double fraction() {
        return (double) health / maxHealth;
    }
}
