package ua.battlesim.gfx.units.internal;

import ua.battlesim.api.Side;

/**
 * Resolves per-role colours for unit models.
 *
 * <p>The side colour is applied to the paint (hull / torso / head) and the
 * vest (brighter). Wheels, tracks, guns and legs use a neutral gunmetal. The
 * shadow is a translucent black blob; the selection marker is a bright ring.
 *
 * <p>Pure and deterministic.
 */
public final class Paint {

    private Paint() {
    }

    /** @return the base RGB {@code [0, 1]} paint colour for a side */
    public static float[] side(Side s) {
        if (s == Side.BLUE) {
            return new float[] { 0.16f, 0.34f, 0.72f };
        }
        if (s == Side.RED) {
            return new float[] { 0.72f, 0.16f, 0.14f };
        }
        return new float[] { 0.30f, 0.30f, 0.30f };
    }

    /**
     * Resolves the RGBA colour for a role, applying side paint, the destroyed
     * (charred) darkening and the selected highlight.
     *
     * @return {@code float[4]} of {@code (r, g, b, a)} in {@code [0, 1]}
     */
    public static float[] color(Role role, float[] side, boolean destroyed, boolean selected) {
        float r, g, b;
        float a = 1.0f;
        switch (role) {
            case HULL:
            case TORSO:
            case HEAD:
                r = side[0];
                g = side[1];
                b = side[2];
                break;
            case VEST:
                r = Math.min(1.0f, side[0] * 1.3f + 0.05f);
                g = Math.min(1.0f, side[1] * 1.3f + 0.05f);
                b = Math.min(1.0f, side[2] * 1.3f + 0.05f);
                break;
            case WHEEL:
            case TRACK:
            case GUN:
            case LEG:
                r = 0.24f;
                g = 0.25f;
                b = 0.27f;
                break;
            case SHADOW:
                r = 0.0f;
                g = 0.0f;
                b = 0.0f;
                a = 0.35f;
                break;
            case MARKER:
                r = 1.0f;
                g = 0.95f;
                b = 0.20f;
                break;
            default:
                r = 0.5f;
                g = 0.5f;
                b = 0.5f;
                break;
        }
        if (destroyed) {
            r *= 0.30f;
            g *= 0.24f;
            b *= 0.20f;
        }
        if (selected) {
            r = Math.min(1.0f, r * 1.3f + 0.08f);
            g = Math.min(1.0f, g * 1.3f + 0.08f);
            b = Math.min(1.0f, b * 1.3f + 0.08f);
        }
        return new float[] { r, g, b, a };
    }
}
