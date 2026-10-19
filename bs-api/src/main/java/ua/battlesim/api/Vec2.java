package ua.battlesim.api;

import static java.lang.Double.isFinite;

/**
 * A 2-D point or vector in the map plane.
 *
 * <p>Units: metres; x = east, z = south. Heading angles are measured as the
 * angle of the (cos, sin) vector in this (x, z) plane, in radians.
 *
 * <p>Immutable and thread-safe.
 */
public record Vec2(double x, double z) {

    public Vec2 {
        if (!isFinite(x)) {
            throw new IllegalArgumentException("x must be finite (metres): " + x);
        }
        if (!isFinite(z)) {
            throw new IllegalArgumentException("z must be finite (metres): " + z);
        }
    }
}
