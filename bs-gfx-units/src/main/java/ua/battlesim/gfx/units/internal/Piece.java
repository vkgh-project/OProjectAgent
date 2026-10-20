package ua.battlesim.gfx.units.internal;

/**
 * A single procedural part of a unit model: a fixed base geometry in local
 * space plus the metadata needed to animate and shade it.
 *
 * <p>Local frame: {@code +X} forward (along the unit heading), {@code +Y} up,
 * {@code +Z} to the right. The base geometry is interleaved
 * {@code pos(3) + normal(3)} triangle soup (stride {@value #STRIDE}).
 *
 * <p>Immutable and thread-safe; the backing array is never mutated.
 */
public final class Piece {

    /** Floats per vertex: position (3) + normal (3). */
    public static final int STRIDE = 6;

    public final Role role;
    public final float[] base;
    /** Local-space pivot for part animation (wheel centre, gun base, hip). */
    public final float px;
    public final float py;
    public final float pz;
    /** Leg swing sign: {@code +1} left, {@code -1} right (ignored by other roles). */
    public final double animSign;

    public Piece(Role role, float[] base, float px, float py, float pz, double animSign) {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        if (base == null) {
            throw new IllegalArgumentException("base must not be null");
        }
        if (base.length % STRIDE != 0) {
            throw new IllegalArgumentException("base length must be a multiple of " + STRIDE + ": " + base.length);
        }
        for (float v : base) {
            if (!Float.isFinite(v)) {
                throw new IllegalArgumentException("base contains a non-finite component");
            }
        }
        this.role = role;
        this.base = base;
        this.px = px;
        this.py = py;
        this.pz = pz;
        this.animSign = animSign;
    }

    public Piece(Role role, float[] base, float px, float py, float pz) {
        this(role, base, px, py, pz, 1.0);
    }

    /** @return the number of vertices in this piece */
    public int vertexCount() {
        return base.length / STRIDE;
    }
}
