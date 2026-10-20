package ua.battlesim.gfx.fx;

import ua.battlesim.api.WeatherState;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the pure sky palette mapping.
 */
class SkyColorsTest {

    private static WeatherState state(double rain, double heavyRain, double snow, double cloud) {
        return new WeatherState(rain, heavyRain, snow, 8.0, StrictMath.PI / 4.0, cloud, 0.4, 0.4, 0.2);
    }

    @Test
    void clearDay_isBlueAndBright() {
        SkyColors c = SkyColors.from(state(0.0, 0.0, 0.0, 0.0));
        assertTrue(c.topB() > c.topR(), "clear sky should be blue");
        assertTrue(c.lightR() > 0.9 && c.lightG() > 0.9 && c.lightB() > 0.8, "clear light should be bright");
        assertEquals(0.0, c.cloudAmount(), 1e-12);
    }

    @Test
    void storm_isDarker() {
        SkyColors clear = SkyColors.from(state(0.0, 0.0, 0.0, 0.0));
        SkyColors storm = SkyColors.from(state(1.0, 1.0, 0.0, 1.0));
        assertTrue(storm.lightR() < clear.lightR());
        assertTrue(storm.lightG() < clear.lightG());
        assertTrue(storm.topR() < clear.topR());
        assertTrue(storm.cloudAmount() > 0.9);
    }

    @Test
    void allComponents_inUnitInterval() {
        SkyColors c = SkyColors.from(state(1.0, 1.0, 1.0, 1.0));
        double[] all = {
                c.topR(), c.topG(), c.topB(),
                c.horizonR(), c.horizonG(), c.horizonB(),
                c.sunR(), c.sunG(), c.sunB(),
                c.lightR(), c.lightG(), c.lightB(),
                c.cloudR(), c.cloudG(), c.cloudB(),
                c.cloudAmount()
        };
        for (double v : all) {
            assertTrue(Double.isFinite(v) && v >= 0.0 && v <= 1.0, "component out of range: " + v);
        }
    }

    @Test
    void deterministic_andValidates() {
        WeatherState w = state(0.3, 0.2, 0.1, 0.5);
        assertEquals(SkyColors.from(w), SkyColors.from(w));
        assertThrows(NullPointerException.class, () -> SkyColors.from(null));
        assertThrows(IllegalArgumentException.class, () -> new SkyColors(
                0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 1.5));
    }
}