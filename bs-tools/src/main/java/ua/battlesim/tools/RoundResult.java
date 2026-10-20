package ua.battlesim.tools;

import java.util.Objects;

import ua.battlesim.api.Outcome;

/**
 * The result of one headless round: how it ended, for how long it ran, how
 * many units of each side survived, the deterministic state hash and the wall
 * time it took to simulate.
 *
 * <p>Units: {@code simulatedSeconds} in simulated seconds
 * ({@code tick x stepSeconds}); {@code wallSeconds} in wall-clock seconds;
 * the survivor counts are whole numbers of units still alive at the end of the
 * round; {@code stateHash} is the engine's 64-bit deterministic state hash.
 *
 * <p>Immutable and thread-safe.
 */
public record RoundResult(int round, Outcome outcome, double simulatedSeconds,
        int survivorsBlue, int survivorsRed, long stateHash, double wallSeconds) {

    public RoundResult {
        if (round < 1) {
            throw new IllegalArgumentException("round must be >= 1: " + round);
        }
        Objects.requireNonNull(outcome, "outcome");
        if (!Double.isFinite(simulatedSeconds) || simulatedSeconds < 0.0) {
            throw new IllegalArgumentException(
                    "simulatedSeconds must be finite and >= 0: " + simulatedSeconds);
        }
        if (survivorsBlue < 0) {
            throw new IllegalArgumentException("survivorsBlue must be >= 0: " + survivorsBlue);
        }
        if (survivorsRed < 0) {
            throw new IllegalArgumentException("survivorsRed must be >= 0: " + survivorsRed);
        }
        if (!Double.isFinite(wallSeconds) || wallSeconds < 0.0) {
            throw new IllegalArgumentException("wallSeconds must be finite and >= 0: " + wallSeconds);
        }
    }

    /** @return the total number of surviving units (both sides) */
    public int survivors() {
        return survivorsBlue + survivorsRed;
    }
}
