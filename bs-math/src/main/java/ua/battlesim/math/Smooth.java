package ua.battlesim.math;

/**
 * Stateless interpolation and clamping helpers (the {@code Smooth} group of
 * the bs-math public API).
 *
 * <p>All methods are pure functions: no state (static or instance), no
 * randomness, IEEE-754 double arithmetic — the same inputs always give the
 * same outputs. Thread-safe.
 */
public final class Smooth {

    private Smooth() {
        throw new AssertionError("Smooth is a static utility class");
    }

    /**
     * @param v   the value to clamp, finite
     * @param min the lower bound, finite
     * @param max the upper bound, finite, {@code >= min}
     * @return {@code min} if {@code v < min}, {@code max} if {@code v > max},
     *         otherwise {@code v}
     * @throws IllegalArgumentException if a bound is not finite or {@code max < min}
     */
    public static double clamp(double v, double min, double max) {
        if (!Double.isFinite(v)) {
            throw new IllegalArgumentException("v must be finite: " + v);
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            throw new IllegalArgumentException("bounds must be finite: [" + min + ", " + max + "]");
        }
        if (max < min) {
            throw new IllegalArgumentException("max must be >= min: [" + min + ", " + max + "]");
        }
        return v < min ? min : v > max ? max : v;
    }

    /**
     * Linear interpolation.
     *
     * @param a the value at {@code t = 0}
     * @param b the value at {@code t = 1}
     * @param t the blend factor (typically in {@code [0, 1]}, extrapolated
     *        outside)
     * @return {@code a + (b - a) * t}
     */
    public static double lerp(double a, double b, double t) {
        if (!Double.isFinite(a) || !Double.isFinite(b) || !Double.isFinite(t)) {
            throw new IllegalArgumentException("arguments must be finite: a=" + a + ", b=" + b + ", t=" + t);
        }
        return a + (b - a) * t;
    }

    /**
     * Smooth Hermite interpolation between two edges: {@code 0} at
     * {@code edge0}, {@code 1} at {@code edge1}, with zero first derivative at
     * both edges; inputs outside {@code [edge0, edge1]} are clamped.
     *
     * @param edge0 the lower edge, finite
     * @param edge1 the upper edge, finite, {@code > edge0}
     * @param x     the input, finite
     * @return a value in {@code [0, 1]}
     * @throws IllegalArgumentException if an argument is not finite or the edges are not ordered
     */
    public static double smoothstep(double edge0, double edge1, double x) {
        if (!Double.isFinite(edge0) || !Double.isFinite(edge1) || !Double.isFinite(x)) {
            throw new IllegalArgumentException(
                    "arguments must be finite: edge0=" + edge0 + ", edge1=" + edge1 + ", x=" + x);
        }
        if (edge0 >= edge1) {
            throw new IllegalArgumentException("edge0 must be < edge1: [" + edge0 + ", " + edge1 + "]");
        }
        double t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * Unit-interval smoothstep: {@code t^2 * (3 - 2t)}.
     *
     * @param t the input, in {@code [0, 1]}
     * @return the smoothed value in {@code [0, 1]}
     * @throws IllegalArgumentException if {@code t} is not finite or not in {@code [0, 1]}
     */
    public static double smoothstep(double t) {
        if (!Double.isFinite(t)) {
            throw new IllegalArgumentException("t must be finite: " + t);
        }
        if (t < 0.0 || t > 1.0) {
            throw new IllegalArgumentException("t must be in [0, 1]: " + t);
        }
        return t * t * (3.0 - 2.0 * t);
    }
}
