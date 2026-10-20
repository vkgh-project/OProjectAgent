package ua.battlesim.gfx.fx.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the tracer travel-time model and pool bookkeeping.
 */
class TracerPoolTest {

    @Test
    void travelSeconds_clampedToSpecWindow() {
        assertEquals(0.06, TracerPool.travelSeconds(1.0), 1e-9);      // 1 m / 600 m/s = 0.0016 s -> clamp
        assertEquals(0.06, TracerPool.travelSeconds(35.0), 1e-9);     // below the window
        assertEquals(0.4, TracerPool.travelSeconds(240.0), 1e-9);     // inside the window
        assertEquals(0.8, TracerPool.travelSeconds(480.0), 1e-9);     // edge of the window
        assertEquals(0.8, TracerPool.travelSeconds(5000.0), 1e-9);    // above the window
    }

    @Test
    void travelSeconds_validates() {
        assertThrows(IllegalArgumentException.class, () -> TracerPool.travelSeconds(-1.0));
        assertThrows(IllegalArgumentException.class, () -> TracerPool.travelSeconds(Double.NaN));
    }

    @Test
    void tip_travelsFromStartToEnd() {
        TracerPool pool = new TracerPool();
        pool.add(0, 0, 0, 240, 0, 0, 0.0);
        assertEquals(1, pool.count());
        assertEquals(0.0, pool.progress(0, 0.0), 1e-12);
        assertEquals(0.5, pool.progress(0, 0.2), 1e-12); // 0.2 / 0.4 s
        assertEquals(120.0, pool.tipX(0, 0.2), 1e-9);
        assertEquals(1.0, pool.progress(0, 0.4), 1e-12);
        assertEquals(240.0, pool.tipX(0, 1.0), 1e-9);
        assertEquals(0.4, pool.impactTime(0), 1e-12);
    }

    @Test
    void removeExpired_preservesOrder() {
        TracerPool pool = new TracerPool();
        pool.add(0, 0, 0, 60, 0, 0, 0.0);    // 0.06 s -> lands at 0.06
        pool.add(0, 0, 0, 480, 0, 0, 1.0);   // 0.8 s  -> lands at 1.8
        pool.add(0, 0, 0, 120, 0, 0, 2.0);   // 0.2 s  -> lands at 2.2
        assertEquals(3, pool.count());
        int removed = pool.removeExpired(1.79);
        assertEquals(1, removed);
        assertEquals(2, pool.count());
        // The two long-range shells survive, in original order.
        assertEquals(1.8, pool.impactTime(0), 1e-12); // t0 = 1.0, travel 0.8 s
        assertEquals(2.2, pool.impactTime(1), 1e-12); // t0 = 2.0, travel 0.2 s
        // Second cleanup removes the rest.
        assertEquals(2, pool.removeExpired(3.0));
        assertEquals(0, pool.count());
    }

    @Test
    void growsBeyondInitialCapacity() {
        TracerPool pool = new TracerPool();
        for (int i = 0; i < 100; i++) {
            pool.add(0, 0, 0, 240, 0, i, 0.0);
        }
        assertEquals(100, pool.count());
        // 240 m in x (plus a small z offset) lands before 0.5 s; fully arrived.
        assertEquals(240.0, pool.tipX(99, 0.5), 1e-9);
    }

    @Test
    void validation() {
        TracerPool pool = new TracerPool();
        assertThrows(IllegalArgumentException.class, () -> pool.add(Double.NaN, 0, 0, 1, 0, 0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> pool.add(0, 0, 0, 1, 0, 0, -0.1));
        assertThrows(IndexOutOfBoundsException.class, () -> pool.progress(0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> pool.removeExpired(Double.POSITIVE_INFINITY));
    }
}