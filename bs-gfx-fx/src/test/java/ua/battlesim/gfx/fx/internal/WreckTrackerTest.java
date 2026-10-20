package ua.battlesim.gfx.fx.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the wreck visibility rules: a wreck becomes visible only after
 * the last killing shell lands and keeps emitting for 90 simulated seconds.
 */
class WreckTrackerTest {

    @Test
    void visibleFromEventTime_whenNoPendingImpact() {
        WreckTracker t = new WreckTracker();
        t.noteDestroyed(7, 100, 0.5, 200, 5.0);
        assertTrue(t.known(7));
        assertFalse(t.visible(7, 4.999));
        assertTrue(t.visible(7, 5.0));
        assertEquals(5.0, t.visibleAt(7), 1e-12);
    }

    @Test
    void visibleAfterLastKillingShellLands() {
        WreckTracker t = new WreckTracker();
        // Normal event order: the killing attack first, the destruction after.
        t.noteKillingImpact(3, 7.3); // shell lands at 7.3 s
        t.noteDestroyed(3, 100, 0.5, 200, 5.0);
        // Not visible at the destruction event time...
        assertFalse(t.visible(3, 5.0));
        assertFalse(t.visible(3, 7.2));
        // ...but visible once the shell has landed.
        assertTrue(t.visible(3, 7.3));
        assertEquals(7.3, t.visibleAt(3), 1e-12);
    }

    @Test
    void laterImpactDelaysVisibility() {
        WreckTracker t = new WreckTracker();
        t.noteKillingImpact(3, 7.3);
        t.noteDestroyed(3, 100, 0.5, 200, 5.0);
        // A second recorded impact (worst case) dominates.
        t.noteKillingImpact(3, 9.1);
        assertEquals(9.1, t.visibleAt(3), 1e-12);
        assertFalse(t.visible(3, 7.5));
        assertTrue(t.visible(3, 9.1));
    }

    @Test
    void effectEnd_isVisiblePlus90s() {
        WreckTracker t = new WreckTracker();
        t.noteDestroyed(1, 0, 0.5, 0, 2.0);
        assertEquals(92.0, t.effectEnd(1), 1e-12);
        assertEquals(WreckTracker.EFFECT_SECONDS, 90.0, 1e-12);
    }

    @Test
    void idsStaySortedAscending() {
        WreckTracker t = new WreckTracker();
        t.noteDestroyed(30, 0, 0, 0, 0);
        t.noteDestroyed(10, 0, 0, 0, 0);
        t.noteDestroyed(20, 0, 0, 0, 0);
        assertEquals(3, t.count());
        assertEquals(10, t.id(0));
        assertEquals(20, t.id(1));
        assertEquals(30, t.id(2));
    }

    @Test
    void prune_dropsExpiredWrecksOnly() {
        WreckTracker t = new WreckTracker();
        t.noteDestroyed(1, 0, 0.5, 0, 0.0);   // ended at 90 s
        t.noteDestroyed(2, 0, 0.5, 0, 50.0);  // ends at 140 s
        int removed = t.prune(100.0);
        assertEquals(1, removed);
        assertEquals(1, t.count());
        assertEquals(2, t.id(0));
        // Nothing left to prune later.
        assertEquals(1, t.prune(200.0));
        assertEquals(0, t.count());
    }

    @Test
    void validation() {
        WreckTracker t = new WreckTracker();
        assertThrows(IllegalArgumentException.class, () -> t.noteDestroyed(-1, 0, 0, 0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> t.noteDestroyed(1, Double.NaN, 0, 0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> t.noteKillingImpact(1, -0.5));
        assertThrows(IllegalArgumentException.class, () -> t.prune(Double.NaN));
    }
}