package ua.battlesim.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PerlinNoiseTest {

    @Test
    void sameSeedGivesIdenticalValues() {
        PerlinNoise a = new PerlinNoise(9001L);
        PerlinNoise b = new PerlinNoise(9001L);
        for (double x = -8.0; x <= 8.0; x += 0.5) {
            for (double y = -8.0; y <= 8.0; y += 0.5) {
                assertEquals(a.get(x, y), b.get(x, y), 0.0, "get(" + x + ", " + y + ")");
                assertEquals(a.fbm(x, y, 5, 2.0, 0.5), b.fbm(x, y, 5, 2.0, 0.5), 0.0, "fbm(" + x + ", " + y + ")");
                assertEquals(a.ridged(x, y, 4, 2.0, 0.5), b.ridged(x, y, 4, 2.0, 0.5), 0.0, "ridged(" + x + ", " + y + ")");
            }
        }
    }

    @Test
    void differentSeedsGiveDifferentFields() {
        PerlinNoise a = new PerlinNoise(1L);
        PerlinNoise b = new PerlinNoise(2L);
        boolean differs = false;
        for (double x = 0.0; x <= 10.0 && !differs; x += 0.37) {
            for (double y = 0.0; y <= 10.0 && !differs; y += 0.31) {
                differs = a.get(x, y) != b.get(x, y);
            }
        }
        assertTrue(differs, "different seeds must give different noise");
    }

    @Test
    void getIsWithinMinusOneToOne() {
        PerlinNoise n = new PerlinNoise(2024L);
        for (double x = -16.0; x <= 16.0; x += 0.73) {
            for (double y = -16.0; y <= 16.0; y += 0.57) {
                double v = n.get(x, y);
                assertTrue(v >= -1.0 && v <= 1.0, "get(" + x + ", " + y + ") = " + v + " outside [-1, 1]");
            }
        }
    }

    @Test
    void getIsSmooth() {
        PerlinNoise n = new PerlinNoise(313L);
        for (double x = -10.0; x <= 10.0; x += 0.91) {
            for (double y = -10.0; y <= 10.0; y += 0.83) {
                double d = Math.abs(n.get(x, y) - n.get(x + 0.01, y + 0.01));
                assertTrue(d < 0.1, "noise not smooth near (" + x + ", " + y + "): delta " + d);
            }
        }
    }

    @Test
    void fbmWithOneOctaveEqualsGet() {
        PerlinNoise n = new PerlinNoise(777L);
        for (double x = -6.0; x <= 6.0; x += 1.3) {
            for (double y = -6.0; y <= 6.0; y += 1.1) {
                assertEquals(n.get(x, y), n.fbm(x, y, 1, 2.0, 0.5), 0.0, "fbm 1 octave (" + x + ", " + y + ")");
            }
        }
    }

    @Test
    void fbmIsWithinMinusOneToOne() {
        PerlinNoise n = new PerlinNoise(888L);
        for (double x = -6.0; x <= 6.0; x += 0.89) {
            for (double y = -6.0; y <= 6.0; y += 0.71) {
                double v = n.fbm(x, y, 6, 2.0, 0.5);
                assertTrue(v >= -1.0 && v <= 1.0, "fbm(" + x + ", " + y + ") = " + v + " outside [-1, 1]");
            }
        }
    }

    @Test
    void ridgedIsWithinZeroToOne() {
        PerlinNoise n = new PerlinNoise(999L);
        for (double x = -6.0; x <= 6.0; x += 0.89) {
            for (double y = -6.0; y <= 6.0; y += 0.71) {
                double v = n.ridged(x, y, 5, 2.0, 0.5);
                assertTrue(v >= 0.0 && v <= 1.0, "ridged(" + x + ", " + y + ") = " + v + " outside [0, 1]");
            }
        }
    }

    @Test
    void ridgedWithOneOctaveIsOneMinusAbsGetSquared() {
        PerlinNoise n = new PerlinNoise(101L);
        double x = 2.3;
        double y = -4.1;
        double expected = (1.0 - Math.abs(n.get(x, y))) * (1.0 - Math.abs(n.get(x, y)));
        assertEquals(expected, n.ridged(x, y, 1, 2.0, 0.5), 1e-12, "ridged 1 octave");
    }

    @Test
    void getRejectsNonFiniteInputs() {
        PerlinNoise n = new PerlinNoise(1L);
        assertThrows(IllegalArgumentException.class, () -> n.get(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> n.get(0.0, Double.POSITIVE_INFINITY));
    }

    @Test
    void fbmAndRidgedValidateParameters() {
        PerlinNoise n = new PerlinNoise(1L);
        assertThrows(IllegalArgumentException.class, () -> n.fbm(0.0, 0.0, 0, 2.0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> n.fbm(0.0, 0.0, 3, 0.5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> n.fbm(0.0, 0.0, 3, 2.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> n.fbm(0.0, 0.0, 3, 2.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> n.ridged(0.0, 0.0, -1, 2.0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> n.ridged(Double.NaN, 0.0, 2, 2.0, 0.5));
    }
}
