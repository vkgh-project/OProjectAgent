package ua.battlesim.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.SimConfig;

/**
 * Budget check: the simulation loop sustains more than 1000 ticks per second
 * with 40 units (20 per side) in the steady state (initial path-planning
 * warm-up excluded).
 */
final class ThroughputTest {

    private static final SimConfig CFG_40_UNITS =
            new SimConfig(2000.0, 0.1, 20, 20000, 20, 20, 20, false);

    @Test
    void exceedsOneThousandTicksPerSecondWith40Units() {
        BattleSimRunner runner = new BattleSimRunner(3, 1, CFG_40_UNITS);
        double tps = runner.measureThroughput(CFG_40_UNITS, 60, 400);
        assertTrue(tps > 1000.0,
                "expected more than 1000 ticks/s with 40 units but measured " + tps + " ticks/s");
    }
}
