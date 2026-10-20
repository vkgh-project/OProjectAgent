package ua.battlesim.gfx.fx.internal;

/**
 * The effect particle categories.
 *
 * <p>Each category owns a slice of the fixed particle pool (see
 * {@link ParticlePool}); the {@link #poolFraction()} is the share of the
 * default 24000-slot pool reserved for that category. Additive types are
 * drawn with {@code GL_ONE, GL_ONE} blending (fire, sparks, flashes); the
 * rest are drawn with standard alpha blending (smoke, dust, rain, snow).
 */
public enum ParticleType {

    /** The bright gun flash at the muzzle (additive). */
    MUZZLE_FLASH(256.0 / 24000.0, true),

    /** The bright flash when a shell impacts (additive). */
    IMPACT_FLASH(512.0 / 24000.0, true),

    /** Burning flames of an explosion or wreck (additive). */
    FIRE(4096.0 / 24000.0, true),

    /** Fast, short-lived ballistic sparks (additive). */
    SPARK(4096.0 / 24000.0, true),

    /** Expanding ground dust of an impact. */
    DUST(2048.0 / 24000.0, false),

    /** Soft, slowly rising smoke of explosions and wrecks. */
    SMOKE(8192.0 / 24000.0, false),

    /** Falling rain streaks around the camera target. */
    RAIN(2560.0 / 24000.0, false),

    /** Drifting snow flakes around the camera target. */
    SNOW(2240.0 / 24000.0, false);

    private final double poolFraction;
    private final boolean additive;

    ParticleType(double poolFraction, boolean additive) {
        this.poolFraction = poolFraction;
        this.additive = additive;
    }

    /** @return the share of the default pool reserved for this type */
    public double poolFraction() {
        return poolFraction;
    }

    /** @return true when this type is drawn with additive blending */
    public boolean additive() {
        return additive;
    }
}
