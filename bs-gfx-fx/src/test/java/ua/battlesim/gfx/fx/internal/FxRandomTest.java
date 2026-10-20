package ua.battlesim.gfx.fx.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Determinism and range tests for the deterministic random source.
 */
class FxRandomTest {

    @Test
    void sameSeed_sameSequence() {
        FxRandom a = new FxRandom(12345L);
        FxRandom b = new FxRandom(12345L);
        for (int i = 0; i < 200; i++) {
            assertEquals(a.nextLong(), b.nextLong());
            assertEquals(a.nextDouble(), b.nextDouble());
        }
    }

    @Test
    void differentSeeds_differentSequence() {
        FxRandom a = new FxRandom(1L);
        FxRandom b = new FxRandom(2L);
        boolean differs = false;
        for (int i = 0; i < 20; i++) {
            if (a.nextLong() != b.nextLong()) {
                differs = true;
                break;
            }
        }
        assertTrue(differs, "sequences from different seeds must differ");
    }

    @Test
    void nextDouble_inUnitRange() {
        FxRandom r = new FxRandom(42L);
        for (int i = 0; i < 1000; i++) {
            double v = r.nextDouble();
            assertTrue(v >= 0.0 && v < 1.0, "out of [0,1): " + v);
        }
    }

    @Test
    void nextDouble_range() {
        FxRandom r = new FxRandom(42L);
        for (int i = 0; i < 1000; i++) {
            double v = r.nextDouble(2.0, 5.0);
            assertTrue(v >= 2.0 && v < 5.0, "out of [2,5): " + v);
        }
    }

    @Test
    void nextInt_ranges() {
        FxRandom r = new FxRandom(42L);
        for (int i = 0; i < 1000; i++) {
            int v = r.nextInt(7);
            assertTrue(v >= 0 && v < 7, "out of [0,7): " + v);
            int w = r.nextInt(3, 10);
            assertTrue(w >= 3 && w < 10, "out of [3,10): " + w);
        }
    }

    @Test
    void validation() {
        FxRandom r = new FxRandom(42L);
        assertThrows(IllegalArgumentException.class, () -> r.nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> r.nextInt(5, 3));
        assertThrows(IllegalArgumentException.class, () -> r.nextDouble(1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> r.nextDouble(Double.NaN, 1.0));
    }
}