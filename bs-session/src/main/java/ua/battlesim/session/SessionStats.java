package ua.battlesim.session;

import static java.lang.Double.isFinite;

import ua.battlesim.api.Outcome;

/**
 * Aggregate, session-wide statistics: how many rounds have finished, how each
 * of them finished, and the result plus simulated duration of the last
 * completed round.
 *
 * <p>Units: {@code lastDurationSeconds} in seconds (simulated time of the last
 * round, ticks x stepSeconds); the win/draw counters are plain counts of
 * finished rounds.
 *
 * <p>Immutable and thread-safe. {@code lastOutcome} is {@code null} until the
 * first round has completed.
 */
public record SessionStats(int roundsCompleted, int blueWins, int redWins, int draws, int timeDraws,
        Outcome lastOutcome, double lastDurationSeconds) {

    public SessionStats {
        if (roundsCompleted < 0) {
            throw new IllegalArgumentException("roundsCompleted must be >= 0: " + roundsCompleted);
        }
        if (blueWins < 0) {
            throw new IllegalArgumentException("blueWins must be >= 0: " + blueWins);
        }
        if (redWins < 0) {
            throw new IllegalArgumentException("redWins must be >= 0: " + redWins);
        }
        if (draws < 0) {
            throw new IllegalArgumentException("draws must be >= 0: " + draws);
        }
        if (timeDraws < 0) {
            throw new IllegalArgumentException("timeDraws must be >= 0: " + timeDraws);
        }
        if (!isFinite(lastDurationSeconds) || lastDurationSeconds < 0.0) {
            throw new IllegalArgumentException("lastDurationSeconds must be finite and >= 0: " + lastDurationSeconds);
        }
    }

    /** @return the statistics before any round has completed */
    public static SessionStats empty() {
        return new SessionStats(0, 0, 0, 0, 0, null, 0.0);
    }
}