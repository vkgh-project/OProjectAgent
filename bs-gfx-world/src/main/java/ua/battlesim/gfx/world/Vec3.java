package ua.battlesim.gfx.world;

/**
 * An immutable 3-D vector in world space (x east, y up, z south).
 *
 * <p>All components must be finite; invalid values are rejected eagerly so the
 * renderer never has to guard against them.
 *
 * <p>Immutable and thread-safe.
 */
public record Vec3(float x, float y, float z) {

    public Vec3 {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException(
                    "Vec3 components must be finite: (" + x + ", " + y + ", " + z + ")");
        }
    }

    /** Convenience constructor accepting double components. */
    public Vec3(double x, double y, double z) {
        this((float) x, (float) y, (float) z);
    }

    /** @return the unit up vector (0, 1, 0) */
    public static Vec3 up() {
        return new Vec3(0f, 1f, 0f);
    }

    /** @return the euclidean length of this vector */
    public float length() {
        return (float) StrictMath.sqrt(x * x + y * y + z * z);
    }
}