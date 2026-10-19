package ua.battlesim.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SmoothTest {

    @Test
    void clampKeepsValuesInside() {
        assertEquals(0.5, Smooth.clamp(0.5, 0.0, 1.0), 0.0);
        assertEquals(0.0, Smooth.clamp(-5.0, 0.0, 1.0), 0.0);
        assertEquals(1.0, Smooth.clamp(123.0, 0.0, 1.0), 0.0);
        assertEquals(2.0, Smooth.clamp(2.0, -3.0, 2.0), 0.0);
    }

    @Test
    void clampRejectsBadBounds() {
        assertThrows(IllegalArgumentException.class, () -> Smooth.clamp(0.5, 1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Smooth.clamp(Double.NaN, 0.0, 1.0));
    }

    @Test
    void lerpInterpolates() {
        assertEquals(0.0, Smooth.lerp(0.0, 10.0, 0.0), 0.0);
        assertEquals(10.0, Smooth.lerp(0.0, 10.0, 1.0), 0.0);
        assertEquals(5.0, Smooth.lerp(0.0, 10.0, 0.5), 0.0);
        assertEquals(2.0, Smooth.lerp(10.0, 0.0, 0.8), 0.0);
    }

    @Test
    void smoothstepEdgesAndMidpoint() {
        assertEquals(0.0, Smooth.smoothstep(0.0, 1.0, -1.0), 0.0);
        assertEquals(0.0, Smooth.smoothstep(0.0, 1.0, 0.0), 0.0);
        assertEquals(1.0, Smooth.smoothstep(0.0, 1.0, 1.0), 0.0);
        assertEquals(1.0, Smooth.smoothstep(0.0, 1.0, 7.0), 0.0);
        assertEquals(0.5, Smooth.smoothstep(0.0, 1.0, 0.5), 0.0);
    }

    @Test
    void smoothstepIsMonotonic() {
        double prev = -1.0;
        for (double x = 0.0; x <= 1.0; x += 0.1) {
            double v = Smooth.smoothstep(0.0, 1.0, x);
            assertTrue(v >= prev && v <= 1.0, "not monotonic at x=" + x + ", v=" + v);
            prev = v;
        }
    }

    @Test
    void smoothstepUnitFormMatchesCubic() {
        // t^2 * (3 - 2t)
        assertEquals(0.75 * 0.75 * (3.0 - 2.0 * 0.75), Smooth.smoothstep(0.75), 0.0);
        assertEquals(0.5 * 0.5 * (3.0 - 2.0 * 0.5), Smooth.smoothstep(0.5), 0.0);
        assertEquals(0.0, Smooth.smoothstep(0.0), 0.0);
        assertEquals(1.0, Smooth.smoothstep(1.0), 0.0);
    }

    @Test
    void smoothstepUnitFormValidatesRange() {
        assertThrows(IllegalArgumentException.class, () -> Smooth.smoothstep(-0.1));
        assertThrows(IllegalArgumentException.class, () -> Smooth.smoothstep(1.1));
        assertThrows(IllegalArgumentException.class, () -> Smooth.smoothstep(Double.NaN));
    }

    @Test
    void smoothstepEdgesMustBeOrdered() {
        assertThrows(IllegalArgumentException.class, () -> Smooth.smoothstep(1.0, 0.0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> Smooth.smoothstep(1.0, 1.0, 0.5));
    }
}
