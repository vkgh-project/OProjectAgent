package ua.battlesim.gfx.fx.internal;

import ua.battlesim.api.WeatherState;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the deterministic lightning flash schedule.
 */
class LightningTest {

    private static WeatherState weather(double rain, double heavyRain, double snow, double cloud) {
        return new WeatherState(rain, heavyRain, snow, 5.0, 1.0, cloud, 0.5, 0.5, 0.0);
    }

    @Test
    void zeroWithoutHeavyRain() {
        for (double t = 0.0; t < 30.0; t += 0.037) {
            assertEquals(0.0, Lightning.flash(weather(1.0, 0.0, 0.0, 0.8), t), 1e-12);
            assertEquals(0.0, Lightning.flash(weather(0.0, 0.0, 1.0, 1.0), t), 1e-12);
        }
    }

    @Test
    void flashInRange_andDeterministic() {
        WeatherState w = weather(1.0, 1.0, 0.0, 0.9);
        double first = Double.NaN;
        for (double t = 0.0; t < 120.0; t += 0.001) {
            double v = Lightning.flash(w, t);
            assertTrue(v >= 0.0 && v <= 1.0, "out of [0,1] at t=" + t + ": " + v);
            assertEquals(Lightning.flash(w, t), v, 1e-12); // pure function
            if (Double.isNaN(first)) {
                first = v;
            }
        }
        assertTrue(Double.isFinite(first));
    }

    @Test
    void heavyRain_producesStrongFlashes() {
        WeatherState w = weather(1.0, 1.0, 0.0, 0.9);
        double peak = 0.0;
        double peakT = -1.0;
        for (double t = 0.0; t < 60.0; t += 0.005) {
            double v = Lightning.flash(w, t);
            if (v > peak) {
                peak = v;
                peakT = t;
            }
        }
        assertTrue(peak > 0.9, "expected near-full flashes, peak was " + peak);
        // The envelope decays after the peak.
        assertTrue(Lightning.flash(w, peakT + 0.3) < peak);
    }

    @Test
    void validation() {
        WeatherState w = weather(1.0, 1.0, 0.0, 0.9);
        assertThrows(NullPointerException.class, () -> Lightning.flash(null, 1.0));
        assertThrows(IllegalArgumentException.class, () -> Lightning.flash(w, -0.1));
        assertThrows(IllegalArgumentException.class, () -> Lightning.flash(w, Double.NaN));
    }
}