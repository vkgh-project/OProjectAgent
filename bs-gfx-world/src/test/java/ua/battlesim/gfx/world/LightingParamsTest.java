package ua.battlesim.gfx.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies {@link LightingParams} validation and the small {@link Vec3} type.
 */
class LightingParamsTest {

    @Test
    void defaults_areSane() {
        LightingParams p = LightingParams.defaults();
        assertEquals(0.0f, p.timeSeconds(), 0.0f);
        assertEquals(0.0f, p.snow(), 0.0f);
        assertEquals(0.0f, p.wetness(), 0.0f);
        assertEquals(0.0f, p.puddles(), 0.0f);
        float sunLength = p.sunDirection().length();
        assertEquals(0.5f, sunLength, 0.5f); // loosely bounded, finite
    }

    @Test
    void surfaceFactors_areClampedToUnitRange() {
        LightingParams p = new LightingParams(
                Vec3.up(), new Vec3(1, 1, 1), new Vec3(0.3f, 0.3f, 0.3f), new Vec3(0.5f, 0.5f, 0.5f),
                0.0001f, 0.0f, 5.0f, -2.0f, 0.25f, 0.0f, 0.0f);
        assertEquals(1.0f, p.snow(), 0.0f);
        assertEquals(0.0f, p.wetness(), 0.0f);
        assertEquals(0.25f, p.puddles(), 0.0f);
    }

    @Test
    void rejectsNegativeFogDensity() {
        assertThrows(IllegalArgumentException.class, () -> new LightingParams(
                Vec3.up(), new Vec3(1, 1, 1), new Vec3(1, 1, 1), new Vec3(1, 1, 1),
                -0.1f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
    }

    @Test
    void rejectsNonFiniteFogDensity() {
        assertThrows(IllegalArgumentException.class, () -> new LightingParams(
                Vec3.up(), new Vec3(1, 1, 1), new Vec3(1, 1, 1), new Vec3(1, 1, 1),
                Float.NaN, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
    }

    @Test
    void rejectsNullColor() {
        assertThrows(NullPointerException.class, () -> new LightingParams(
                null, new Vec3(1, 1, 1), new Vec3(1, 1, 1), new Vec3(1, 1, 1),
                0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f));
    }

    @Test
    void withTime_updatesTime() {
        LightingParams p = LightingParams.defaults().withTime(12.5f);
        assertEquals(12.5f, p.timeSeconds(), 0.0f);
    }

    @Test
    void vec3_rejectsNonFinite() {
        assertThrows(IllegalArgumentException.class, () -> new Vec3(0, Float.NaN, 0));
    }
}