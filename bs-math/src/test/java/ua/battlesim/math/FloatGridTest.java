package ua.battlesim.math;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FloatGridTest {

    /** A 4x4 grid with value {@code v(x, z) = x + 2z} (exactly representable). */
    private static FloatGrid linearGrid() {
        FloatGrid g = new FloatGrid(4, 4);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                g.set(x, y, (float) (x + 2 * y));
            }
        }
        return g;
    }

    @Test
    void nearestReturnsStoredValues() {
        FloatGrid g = linearGrid();
        assertEquals(5.0f, g.nearest(1.0, 2.0), 0.0f);
        assertEquals(0.0f, g.nearest(0.99, 0.99), 0.0f);
        assertEquals(2.0f, g.nearest(2.999, 0.5), 0.0f);
    }

    @Test
    void nearestClampsOutsideTheGrid() {
        FloatGrid g = linearGrid();
        assertEquals(0.0f, g.nearest(-5.0, -3.0), 0.0f);
        assertEquals(9.0f, g.nearest(1000.0, 1e9), 0.0f);
    }

    @Test
    void bilinearMatchesLinearField() {
        // bilinear interpolation of a linear field is exact
        FloatGrid g = linearGrid();
        assertEquals(0.0f, g.bilinear(0.0, 0.0), 0.0f);
        assertEquals(1.5f, g.bilinear(0.5, 0.5), 1e-6f);
        assertEquals(4.25f, g.bilinear(1.25, 1.5), 1e-6f);
        assertEquals(9.0f, g.bilinear(3.0, 3.0), 0.0f);
        assertEquals(2.75f, g.bilinear(2.75, 0.0), 1e-6f);
    }

    @Test
    void bilinearClampsOutsideTheGrid() {
        FloatGrid g = linearGrid();
        assertEquals(1.0f, g.bilinear(-42.0, 0.5), 0.0f);
        assertEquals(9.0f, g.bilinear(3.0, 42.0), 0.0f);
        assertEquals(6.0f, g.bilinear(-1.0, 1e6), 0.0f);
    }

    @Test
    void bilinearIsDeterministic() {
        FloatGrid a = linearGrid();
        FloatGrid b = linearGrid();
        for (double u = -2.0; u <= 6.0; u += 0.31) {
            for (double v = -2.0; v <= 6.0; v += 0.29) {
                assertEquals(a.bilinear(u, v), b.bilinear(u, v), 0.0f, "bilinear(" + u + ", " + v + ")");
            }
        }
    }

    @Test
    void getAndSetRoundTrip() {
        FloatGrid g = new FloatGrid(3, 2);
        g.set(2, 1, -42.5f);
        assertEquals(-42.5f, g.get(2, 1), 0.0f);
        assertEquals(0.0f, g.get(0, 0), 0.0f, "fresh cells must be zero");
    }

    @Test
    void constructorFromValuesDefensivelyCopies() {
        float[] src = {1, 2, 3, 4, 5, 6};
        FloatGrid g = new FloatGrid(3, 2, src);
        src[0] = 999.0f;
        assertEquals(1.0f, g.get(0, 0), 0.0f, "must not share storage with the input array");
        assertArrayEquals(new float[] {1, 2, 3, 4, 5, 6}, g.values(), 0.0f);
        FloatGrid modified = new FloatGrid(3, 2, g.values());
        g.set(0, 0, -1f);
        assertEquals(1.0f, modified.get(0, 0), 0.0f, "values() must be a defensive copy");
    }

    @Test
    void singleCellGridSamplesCorrectly() {
        FloatGrid g = new FloatGrid(1, 1);
        g.set(0, 0, 7.0f);
        assertEquals(7.0f, g.nearest(-100.0, 100.0), 0.0f);
        assertEquals(7.0f, g.bilinear(0.0, 0.0), 0.0f);
        assertEquals(7.0f, g.bilinear(123.0, -123.0), 0.0f);
    }

    @Test
    void invalidConstructionAndAccessThrow() {
        assertThrows(IllegalArgumentException.class, () -> new FloatGrid(0, 4));
        assertThrows(IllegalArgumentException.class, () -> new FloatGrid(4, -1));
        assertThrows(IllegalArgumentException.class, () -> new FloatGrid(2, 2, new float[] {1, 2, 3}));
        FloatGrid g = new FloatGrid(2, 2);
        assertThrows(IllegalArgumentException.class, () -> g.get(2, 0));
        assertThrows(IllegalArgumentException.class, () -> g.get(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> g.get(0, 2));
        assertThrows(IllegalArgumentException.class, () -> g.set(0, 0, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> g.set(0, 0, Float.POSITIVE_INFINITY));
    }

    @Test
    void samplingRejectsNonFiniteCoordinates() {
        FloatGrid g = new FloatGrid(2, 2);
        assertThrows(IllegalArgumentException.class, () -> g.nearest(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> g.nearest(0.0, Double.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> g.bilinear(Double.POSITIVE_INFINITY, 0.0));
        assertThrows(IllegalArgumentException.class, () -> g.bilinear(0.0, Double.NaN));
    }

    @Test
    void dimensionsAreExposed() {
        FloatGrid g = new FloatGrid(5, 7);
        assertEquals(5, g.width());
        assertEquals(7, g.height());
    }
}
