package ua.battlesim.tools;

import java.util.List;
import java.util.Objects;

import ua.battlesim.api.SimConfig;

/**
 * The aggregate result of a headless run: the per-round results, whether the
 * per-round determinism check (run twice, compare {@code stateHash}) passed,
 * and the throughput of the simulation loop.
 *
 * <p>Units: {@code totalWallSeconds} in wall-clock seconds; {@code totalTickWallSeconds}
 * in wall-clock seconds spent only advancing engine ticks (terrain generation
 * and round setup excluded); {@code ticksPerSecond} as a rate of simulation
 * ticks per wall-clock second.
 *
 * <p>Immutable and thread-safe; the round list is copied defensively.
 */
public record RunReport(long seed, SimConfig config, List<RoundResult> rounds,
        boolean determinismOk, long totalTicks, double totalWallSeconds,
        double totalTickWallSeconds, double ticksPerSecond) {

    public RunReport {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(rounds, "rounds");
        if (rounds.isEmpty()) {
            throw new IllegalArgumentException("rounds must not be empty");
        }
        for (RoundResult r : rounds) {
            Objects.requireNonNull(r, "round result");
        }
        rounds = List.copyOf(rounds);
        if (totalTicks < 0) {
            throw new IllegalArgumentException("totalTicks must be >= 0: " + totalTicks);
        }
        if (!Double.isFinite(totalWallSeconds) || totalWallSeconds < 0.0) {
            throw new IllegalArgumentException(
                    "totalWallSeconds must be finite and >= 0: " + totalWallSeconds);
        }
        if (!Double.isFinite(totalTickWallSeconds) || totalTickWallSeconds < 0.0) {
            throw new IllegalArgumentException(
                    "totalTickWallSeconds must be finite and >= 0: " + totalTickWallSeconds);
        }
        if (!Double.isFinite(ticksPerSecond) || ticksPerSecond < 0.0) {
            throw new IllegalArgumentException(
                    "ticksPerSecond must be finite and >= 0: " + ticksPerSecond);
        }
    }
}
