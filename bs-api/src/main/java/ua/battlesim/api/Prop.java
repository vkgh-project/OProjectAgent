package ua.battlesim.api;

import static java.lang.Double.isFinite;

/**
 * A placed world prop (tree or rock) used for rendering and line-of-sight.
 *
 * <p>Units: x, z in metres (x east, z south); scale as a size multiplier
 * ({@code 1} = natural size); rotation in radians about the up axis; tint in
 * {@code [0, 1]}; kind is the deterministic species/style index.
 *
 * <p>Immutable and thread-safe.
 */
public record Prop(double x, double z, double scale, double rotation, double tint, int kind) {

    public Prop {
        if (!isFinite(x)) {
            throw new IllegalArgumentException("x must be finite (metres): " + x);
        }
        if (!isFinite(z)) {
            throw new IllegalArgumentException("z must be finite (metres): " + z);
        }
        if (!isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException("scale must be finite and > 0: " + scale);
        }
        if (!isFinite(rotation)) {
            throw new IllegalArgumentException("rotation must be finite (radians): " + rotation);
        }
        if (!isFinite(tint) || tint < 0.0 || tint > 1.0) {
            throw new IllegalArgumentException("tint must be finite and in [0,1]: " + tint);
        }
        if (kind < 0) {
            throw new IllegalArgumentException("kind must be >= 0: " + kind);
        }
    }
}
