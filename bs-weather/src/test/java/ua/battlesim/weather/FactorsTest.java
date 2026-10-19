package ua.battlesim.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.WeatherState;

/**
 * Factor checks: exact spec formulas against the published state,
 * clamping bounds, and the permanently-clear disabled mode.
 */
class FactorsTest {

    private static final double S = StrictMath.sqrt(0.5);

    private static final double[][] DIRECTIONS = {
            {1.0, 0.0}, {0.0, 1.0}, {-1.0, 0.0}, {0.0, -1.0},
            {S, S},
            {-S, S},
            {S, -S},
            {-S, -S},
    };

    @Test
    void speedFactorIsWithinSpecClamp() {
        SimpleWeatherModel m = new SimpleWeatherModel(3L, true);
        for (int tick = 0; tick <= 20_000; tick++) {
            m.step(tick);
            for (double[] d : DIRECTIONS) {
                double f = m.speedFactor(d[0], d[1]);
                assertTrue(f >= 0.4 && f <= 1.1, "tick " + tick + " dir " + d
                        + " factor " + f + " outside [0.4, 1.1]");
            }
        }
    }

    @Test
    void speedFactorMatchesTheSpecFormulaOfThePublishedState() {
        SimpleWeatherModel m = new SimpleWeatherModel(11L, true);
        for (int tick = 0; tick <= 20_000; tick++) {
            m.step(tick);
            WeatherState s = m.state();
            for (double[] d : DIRECTIONS) {
                double expected = expectedSpeedFactor(s, d[0], d[1]);
                assertEquals(expected, m.speedFactor(d[0], d[1]), 0.0,
                        "tick " + tick + " dir " + d[0] + "," + d[1]);
            }
        }
    }

    @Test
    void visibilityMatchesTheSpecFormula() {
        SimpleWeatherModel m = new SimpleWeatherModel(5L, true);
        for (int tick = 0; tick <= 20_000; tick++) {
            m.step(tick);
            WeatherState s = m.state();
            double expected = 25000.0 / (1.0 + 5.0 * s.rain()
                    + 24.0 * s.heavyRain() + 10.0 * s.snow());
            assertEquals(expected, m.visibility(), 0.0, "tick " + tick);
            assertTrue(m.visibility() >= 25000.0 / 45.0 && m.visibility() <= 25000.0,
                    "tick " + tick + " visibility out of range: " + m.visibility());
        }
    }

    @Test
    void combatFactorMatchesTheSpecFormula() {
        SimpleWeatherModel m = new SimpleWeatherModel(6L, true);
        for (int tick = 0; tick <= 20_000; tick++) {
            m.step(tick);
            WeatherState s = m.state();
            double visibility = 25000.0 / (1.0 + 5.0 * s.rain()
                    + 24.0 * s.heavyRain() + 10.0 * s.snow());
            double expected = clamp(visibility / 5000.0, 0.3, 1.0);
            assertEquals(expected, m.combatFactor(), 0.0, "tick " + tick);
            assertTrue(m.combatFactor() >= 0.3 && m.combatFactor() <= 1.0,
                    "tick " + tick + " combat factor out of [0.3, 1]: " + m.combatFactor());
        }
    }

    @Test
    void initialStateIsClearBeforeTheFirstStep() {
        SimpleWeatherModel m = new SimpleWeatherModel(99L, true);
        WeatherState s = m.state();
        assertEquals(0.0, s.rain());
        assertEquals(0.0, s.heavyRain());
        assertEquals(0.0, s.snow());
        assertEquals(0.0, s.wetness());
        assertEquals(0.0, s.puddles());
        assertEquals(0.0, s.snowCover());
        assertTrue(s.windSpeed() >= 0.0);
        assertTrue(s.cloud() >= 0.0 && s.cloud() <= 1.0);
    }

    @Test
    void disabledModelIsPermanentlyClearWithTwoMetreWind() {
        SimpleWeatherModel m = new SimpleWeatherModel(21L, false);
        for (int tick = 0; tick <= 1000; tick++) {
            m.step(tick);
        }
        WeatherState s = m.state();
        assertEquals(0.0, s.rain());
        assertEquals(0.0, s.heavyRain());
        assertEquals(0.0, s.snow());
        assertEquals(0.0, s.wetness());
        assertEquals(0.0, s.puddles());
        assertEquals(0.0, s.snowCover());
        assertEquals(2.0, s.windSpeed());
        for (double[] d : DIRECTIONS) {
            double f = m.speedFactor(d[0], d[1]);
            assertTrue(f >= 0.992 && f <= 1.008, "dir " + d[0] + "," + d[1]
                    + " factor " + f + " outside [0.992, 1.008]");
        }
        assertEquals(25000.0, m.visibility());
        assertEquals(1.0, m.combatFactor());
        assertEquals(java.util.List.of("Sunny"), m.conditions());
    }


    @Test
    void conditionsConsistentlyReflectTheState() {
        SimpleWeatherModel m = new SimpleWeatherModel(13L, true);
        for (int tick = 0; tick <= 20_000; tick++) {
            m.step(tick);
            WeatherState s = m.state();
            var conditions = m.conditions();
            for (String c : conditions) {
                assertTrue(java.util.Set.of("Sunny", "Rain", "Heavy rain", "Strong wind", "Snow")
                        .contains(c), "unexpected condition '" + c + "'");
            }
            boolean clear = s.rain() <= 0.5 && s.heavyRain() <= 0.5 && s.snow() <= 0.5;
            assertEquals(clear, conditions.contains("Sunny"), "tick " + tick);
            assertEquals(s.windSpeed() >= 12.0, conditions.contains("Strong wind"),
                    "tick " + tick);
            assertEquals(s.rain() > 0.5, conditions.contains("Rain"), "tick " + tick);
            assertEquals(s.heavyRain() > 0.5, conditions.contains("Heavy rain"),
                    "tick " + tick);
            assertEquals(s.snow() > 0.5, conditions.contains("Snow"), "tick " + tick);
        }
    }

    /** Same arithmetic order as the implementation. */
    private static double expectedSpeedFactor(WeatherState s, double dirX, double dirZ) {
        double windX = StrictMath.cos(s.windDirection()) * s.windSpeed();
        double windZ = StrictMath.sin(s.windDirection()) * s.windSpeed();
        double tailwind = dirX * windX + dirZ * windZ;
        double factor = 1.0
                - 0.08 * s.rain()
                - 0.22 * s.heavyRain()
                - 0.18 * s.snow()
                - 0.10 * s.snowCover()
                - 0.05 * s.puddles()
                + 0.004 * tailwind;
        return clamp(factor, 0.4, 1.1);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }
}
