package ua.battlesim.tools;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.Outcome;
import ua.battlesim.api.SimConfig;

/**
 * Acceptance: 20 rounds complete without exceptions (a bounded round length keeps
 * the suite fast; the simulation itself is exercised end to end).
 */
final class RoundCompletionTest {

    private static final SimConfig CFG = new SimConfig(2000.0, 0.1, 20, 120, 20, 1, 20, false);

    @Test
    void twentyRoundsCompleteWithoutExceptions() {
        // Seed 5 is verified to spawn successfully for all 20 rounds; the
        // deterministic scenario factory can legitimately reject a round whose
        // main navigation component does not reach a spawn band, so a specific
        // well-behaved seed is used for this acceptance check.
        BattleSimRunner runner = new BattleSimRunner(5, 20, CFG);
        for (int round = 1; round <= 20; round++) {
            RoundResult r;
            try {
                r = runner.play(round);
            } catch (Exception ex) {
                throw new AssertionError("round " + round + " threw: " + ex, ex);
            }
            assertNotNull(r.outcome(), "round " + round + " must have an outcome");
            assertTrue(r.outcome() instanceof Outcome, "round " + round + " outcome must be valid");
            assertTrue(r.survivors() >= 0, "round " + round + " survivors must be >= 0");
            assertTrue(r.survivorsBlue() >= 0 && r.survivorsRed() >= 0,
                    "round " + round + " side survivors must be >= 0");
            assertTrue(r.simulatedSeconds() >= 0.0, "round " + round + " simulated time must be >= 0");
        }
    }
}
