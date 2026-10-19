package ua.battlesim.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DetRandomTest {

    @Test
    void sameSeedGivesIdenticalSequences() {
        DetRandom a = new DetRandom(42L);
        DetRandom b = new DetRandom(42L);
        for (int i = 0; i < 200; i++) {
            assertEquals(a.nextLong(), b.nextLong(), "nextLong #" + i);
            assertEquals(a.nextDouble(), b.nextDouble(), "nextDouble #" + i);
            assertEquals(a.nextFloat(), b.nextFloat(), "nextFloat #" + i);
            assertEquals(a.nextInt(97), b.nextInt(97), "nextInt #" + i);
            assertEquals(a.nextBoolean(), b.nextBoolean(), "nextBoolean #" + i);
        }
    }

    @Test
    void differentSeedsGiveDifferentSequences() {
        DetRandom a = new DetRandom(1L);
        DetRandom b = new DetRandom(2L);
        boolean differs = false;
        for (int i = 0; i < 10 && !differs; i++) {
            differs = a.nextLong() != b.nextLong();
        }
        assertTrue(differs, "different seeds must give different sequences");
    }

    @Test
    void nextDoubleIsInRange() {
        DetRandom r = new DetRandom(7L);
        for (int i = 0; i < 100_000; i++) {
            double v = r.nextDouble();
            assertTrue(v >= 0.0 && v < 1.0, "out of [0, 1): " + v);
        }
    }

    @Test
    void nextFloatIsInRange() {
        DetRandom r = new DetRandom(7L);
        for (int i = 0; i < 100_000; i++) {
            float v = r.nextFloat();
            assertTrue(v >= 0.0f && v < 1.0f, "out of [0, 1): " + v);
        }
    }

    @Test
    void meanOfNextDoubleIsNearHalf() {
        DetRandom r = new DetRandom(123L);
        double sum = 0.0;
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            sum += r.nextDouble();
        }
        double mean = sum / n;
        assertTrue(Math.abs(mean - 0.5) < 0.01, "mean " + mean + " not near 0.5");
    }

    @Test
    void nextIntIsInBound() {
        DetRandom r = new DetRandom(99L);
        for (int i = 0; i < 20_000; i++) {
            int v = r.nextInt(10);
            assertTrue(v >= 0 && v < 10, "out of [0, 10): " + v);
        }
    }

    @Test
    void nextIntWithBoundOneAlwaysZero() {
        DetRandom r = new DetRandom(5L);
        for (int i = 0; i < 100; i++) {
            assertEquals(0, r.nextInt(1));
        }
    }

    @Test
    void nextIntRejectsBadBound() {
        DetRandom r = new DetRandom(5L);
        assertThrows(IllegalArgumentException.class, () -> r.nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> r.nextInt(-3));
    }

    @Test
    void nextIntCoversAllResidues() {
        DetRandom r = new DetRandom(11L);
        boolean[] seen = new boolean[7];
        for (int i = 0; i < 1000 && !allSeen(seen); i++) {
            seen[r.nextInt(7)] = true;
        }
        assertTrue(allSeen(seen), "nextInt(7) must cover all residues");
    }

    @Test
    void nextBooleanYieldsBothValues() {
        DetRandom r = new DetRandom(21L);
        boolean sawTrue = false;
        boolean sawFalse = false;
        for (int i = 0; i < 100; i++) {
            sawTrue |= r.nextBoolean();
            sawFalse |= !r.nextBoolean();
        }
        assertTrue(sawTrue, "nextBoolean must return true sometimes");
        assertTrue(sawFalse, "nextBoolean must return false sometimes");
    }

    @Test
    void forkIsDeterministic() {
        DetRandom a = new DetRandom(2024L);
        DetRandom b = new DetRandom(2024L);
        a.nextLong();
        b.nextLong();
        DetRandom fa = a.fork(42L);
        DetRandom fb = b.fork(42L);
        for (int i = 0; i < 50; i++) {
            assertEquals(fa.nextLong(), fb.nextLong(), "forked stream #" + i);
        }
    }

    @Test
    void forkDoesNotDisturbParentSequence() {
        DetRandom a = new DetRandom(31337L);
        DetRandom b = new DetRandom(31337L);
        a.nextLong();
        a.fork(123L);
        b.nextLong();
        for (int i = 0; i < 20; i++) {
            assertEquals(a.nextLong(), b.nextLong(), "parent sequence disturbed by fork #" + i);
        }
    }

    @Test
    void forkWithDifferentSaltsGivesDifferentStreams() {
        DetRandom a = new DetRandom(555L);
        DetRandom b = new DetRandom(555L);
        boolean differs = false;
        for (int i = 0; i < 10 && !differs; i++) {
            differs = a.fork(1L).nextLong() != b.fork(2L).nextLong();
        }
        assertTrue(differs, "different salts must give different streams");
        // forked stream must differ from the parent stream itself
        DetRandom parent = new DetRandom(555L);
        DetRandom child = parent.fork(9L);
        long childValue = child.nextLong();
        boolean differsFromParent = false;
        for (int i = 0; i < 5 && !differsFromParent; i++) {
            differsFromParent = parent.nextLong() != childValue;
            if (!differsFromParent) {
                childValue = child.nextLong();
            }
        }
        assertTrue(differsFromParent, "forked stream should be independent of the parent stream");
    }

    private static boolean allSeen(boolean[] seen) {
        for (boolean b : seen) {
            if (!b) {
                return false;
            }
        }
        return true;
    }
}
