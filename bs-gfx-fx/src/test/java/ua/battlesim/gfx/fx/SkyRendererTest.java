package ua.battlesim.gfx.fx;

import ua.battlesim.api.WeatherState;
import ua.battlesim.gfx.core.OrbitCamera;

import org.joml.Vector3f;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Contract tests for {@link SkyRenderer} that do not require an OpenGL
 * context: palette and lightning math (pure CPU) and argument validation.
 */
class SkyRendererTest {

    private static WeatherState weather(double rain, double heavyRain, double snow, double cloud) {
        return new WeatherState(rain, heavyRain, snow, 6.0, 1.0, cloud, 0.5, 0.5, 0.0);
    }

    @Test
    void colors_delegatesToPalette() {
        WeatherState w = weather(0.2, 0.1, 0.0, 0.4);
        assertEquals(SkyColors.from(w), SkyRenderer.colors(w));
        assertTrue(SkyRenderer.colors(w).lightR() > 0.0);
    }

    @Test
    void lightningFlash_isPureAndBounded() {
        WeatherState calm = weather(0.0, 0.0, 0.0, 0.5);
        WeatherState storm = weather(1.0, 1.0, 0.0, 1.0);
        for (double t = 0.0; t < 20.0; t += 0.011) {
            assertEquals(0.0, SkyRenderer.lightningFlash(calm, t), 1e-12);
            double v = SkyRenderer.lightningFlash(storm, t);
            assertTrue(v >= 0.0 && v <= 1.0);
            assertEquals(SkyRenderer.lightningFlash(storm, t), v, 1e-12);
        }
    }

    @Test
    void render_validatesArgumentsBeforeGpu() {
        SkyRenderer r = new SkyRenderer();
        WeatherState w = weather(0.0, 0.0, 0.0, 0.0);
        Vector3f sun = new Vector3f(0.6f, 0.7f, 0.4f);
        OrbitCamera camera = new OrbitCamera();
        assertThrows(NullPointerException.class, () -> r.render(null, 1.0, sun, camera));
        assertThrows(IllegalArgumentException.class, () -> r.render(w, -1.0, sun, camera));
        assertThrows(IllegalArgumentException.class, () -> r.render(w, Double.NaN, sun, camera));
        assertThrows(NullPointerException.class, () -> r.render(w, 1.0, null, camera));
        assertThrows(IllegalArgumentException.class,
                () -> r.render(w, 1.0, new Vector3f(Float.NaN, 0.0f, 0.0f), camera));
        assertThrows(NullPointerException.class, () -> r.render(w, 1.0, sun, null));
        r.dispose();
    }

    @Test
    void dispose_isIdempotent() {
        SkyRenderer r = new SkyRenderer();
        assertDoesNotThrow(r::dispose);
        assertDoesNotThrow(r::dispose);
        assertDoesNotThrow(r::close);
    }
}