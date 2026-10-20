package ua.battlesim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.SimConfig;

/**
 * Determinism check: each round is played twice and the two {@code stateHash}
 * values (plus outcome and survivors) must be identical — the headless runner
 * is a pure function of its inputs.
 */
final class DeterminismTest {

    private static final SimConfig CFG = new SimConfig(2000.0, 0.1, 20, 400, 20, 1, 20, false);

    @Test
    void sameRoundPlayedTwiceHasIdenticalHash() {
        BattleSimRunner runner = new BattleSimRunner(7, 1, CFG);
        for (int round = 1; round <= 3; round++) {
            RoundResult a = runner.play(round);
            RoundResult b = runner.play(round);
            assertEquals(a.stateHash(), b.stateHash(), "round " + round + " stateHash must be stable");
            assertEquals(a.outcome(), b.outcome(), "round " + round + " outcome must be stable");
            assertEquals(a.survivorsBlue(), b.survivorsBlue(), "round " + round + " BLUE survivors must be stable");
            assertEquals(a.survivorsRed(), b.survivorsRed(), "round " + round + " RED survivors must be stable");
            assertEquals(a.simulatedSeconds(), b.simulatedSeconds(),
                    "round " + round + " simulated time must be stable");
        }
    }

    @Test
    void runReportsDeterminismOk() {
        RunReport report = new BattleSimRunner(42, 3, CFG).run();
        assertEquals(3, report.rounds().size());
        assertTrue(report.determinismOk(), "run must report determinism OK");
        assertTrue(report.totalTicks() > 0, "run must simulate at least one tick");
        assertTrue(report.ticksPerSecond() > 0.0, "run must report a positive throughput");
    }
}
