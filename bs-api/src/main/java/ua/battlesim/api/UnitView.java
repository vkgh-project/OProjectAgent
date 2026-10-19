package ua.battlesim.api;

import java.util.Objects;

import static java.lang.Double.isFinite;

/**
 * Immutable per-unit view for rendering: current and previous-frame values so
 * the presentation layer can interpolate.
 *
 * <p>Units: position/odometer in metres; heading/aim in radians (angle of the
 * (cos, sin) vector in the (x, z) plane, x east, z south); health in
 * {@code [0, 100]}; {@code destroyedTick} in ticks ({@code -1} when not
 * destroyed).
 *
 * <p>Immutable and thread-safe; safe to read from the render thread.
 */
public record UnitView(int id, Side side, UnitType type, double x, double z, double prevX, double prevZ,
        double heading, double prevHeading, double aim, double prevAim,
        int health, UnitState state, double odometer, int destroyedTick) {

    public UnitView {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0: " + id);
        }
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        for (double v : new double[] { x, z, prevX, prevZ, heading, prevHeading, aim, prevAim, odometer }) {
            if (!isFinite(v)) {
                throw new IllegalArgumentException("UnitView numeric fields must be finite, got: " + v);
            }
        }
        if (health < 0 || health > 100) {
            throw new IllegalArgumentException("health must be in [0,100]: " + health);
        }
        if (destroyedTick < -1) {
            throw new IllegalArgumentException("destroyedTick must be >= -1: " + destroyedTick);
        }
    }
}
