package ua.battlesim.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ua.battlesim.api.WeatherState;
import ua.battlesim.scenario.internal.RoundWeather;

import org.junit.jupiter.api.Test;

/**
 * Unit tests of the internal {@link RoundWeather} model: determinism,
 * re-step no-op, channel ranges and factor bounds.
 */
final class RoundWeatherTest {

    @Test
    void sameSeedGivesIdenticalStateSequence() {
        RoundWeather a = new RoundWeather(999L, true);
        RoundWeather b = new RoundWeather(999L, true);
        for (int tick = 0; tick < 100; tick++) {
            a.step(tick);
            b.step(tick);
            assertEquals(a.state(), b.state(), "tick " + tick);
            assertEquals(a.speedFactor(1.0, 0.0), b.speedFactor(1.0, 0.0), 0.0, "tick " + tick);
        }
    }

    @Test
    void differentSeedsDiverge() {
        RoundWeather a = new RoundWeather(111L, true);
        RoundWeather b = new RoundWeather(222L, true);
        boolean differs = false;
        for (int tick = 0; tick < 50 && !differs; tick++) {
            a.step(tick);
            b.step(tick);
            differs = !a.state().equals(b.state());
        }
        assertTrue(differs, "different seeds must give different weather");
    }

    @Test
    void steppingBackwardsOrToTheSameTickIsANoOp() {
        RoundWeather w = new RoundWeather(31337L, true);
        w.step(10);
        WeatherState at10 = w.state();
        w.step(5);
        assertEquals(at10, w.state(), "step(5) after step(10) must be a no-op");
        w.step(10);
        assertEquals(at10, w.state(), "re-stepping tick 10 must be a no-op");
    }

    @Test
    void channelsStayInRangeOverManyTicks() {
        RoundWeather w = new RoundWeather(424242L, true);
        for (int tick = 0; tick < 500; tick++) {
            w.step(tick);
            WeatherState s = w.state();
            assertTrue(s.rain() >= 0.0 && s.rain() <= 1.0, "rain tick " + tick);
            assertTrue(s.heavyRain() >= 0.0 && s.heavyRain() <= 1.0, "heavy tick " + tick);
            assertTrue(s.snow() >= 0.0 && s.snow() <= 1.0, "snow tick " + tick);
            assertTrue(s.windSpeed() >= 0.0, "windSpeed tick " + tick);
            assertTrue(s.windDirection() > -Math.PI && s.windDirection() <= Math.PI,
                    "windDirection tick " + tick);
            assertTrue(s.cloud() >= 0.0 && s.cloud() <= 1.0, "cloud tick " + tick);
            assertTrue(s.wetness() >= 0.0 && s.wetness() <= 1.0, "wetness tick " + tick);
            assertTrue(s.puddles() >= 0.0 && s.puddles() <= 1.0, "puddles tick " + tick);
            assertTrue(s.snowCover() >= 0.0 && s.snowCover() <= 1.0, "snowCover tick " + tick);
        }
    }

    @Test
    void factorsStayInContractBounds() {
        RoundWeather w = new RoundWeather(314159L, true);
        for (int tick = 0; tick < 200; tick++) {
            w.step(tick);
            for (double angle = 0.0; angle < Math.PI; angle += Math.PI / 4.0) {
                double f = w.speedFactor(Math.cos(angle), Math.sin(angle));
                assertTrue(f >= 0.25 && f <= 1.0,
                        "speedFactor " + f + " out of [0.25, 1] at tick " + tick);
            }
            assertTrue(w.speedFactor(0.0, 0.0) == 1.0, "zero direction gives 1");
            assertTrue(w.visibility() >= 0.1 && w.visibility() <= 1.0, "visibility tick " + tick);
            assertTrue(w.combatFactor() >= 0.3 && w.combatFactor() <= 1.0,
                    "combatFactor tick " + tick);
        }
    }

    @Test
    void disabledWeatherIsClearWithUnitFactors() {
        RoundWeather w = new RoundWeather(1L, false);
        w.step(7);
        assertEquals(0.0, w.state().rain(), 0.0);
        assertEquals(0.0, w.state().windSpeed(), 0.0);
        assertEquals(1.0, w.speedFactor(0.0, 1.0), 0.0);
        assertEquals(1.0, w.visibility(), 0.0);
        assertEquals(1.0, w.combatFactor(), 0.0);
    }

    @Test
    void speedFactorRejectsNonFiniteDirections() {
        RoundWeather w = new RoundWeather(1L, true);
        w.step(0);
        assertThrows(IllegalArgumentException.class, () -> w.speedFactor(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> w.speedFactor(1.0, Double.POSITIVE_INFINITY));
    }

    @Test
    void seedAccessorReflectsConstruction() {
        assertNotEquals(new RoundWeather(1L, true).seed(), new RoundWeather(2L, true).seed());
    }
}