package ua.battlesim.api;

/**
 * Aggregate statistics of the current round, exposed to the presentation
 * layer.
 *
 * <p>Counts are numbers of units still alive (health &gt; 0, state not
 * {@link UnitState#DESTROYED}).
 *
 * <p>Immutable and thread-safe.
 */
public record Stats(int blueRemaining, int redRemaining) {

    public Stats {
        if (blueRemaining < 0) {
            throw new IllegalArgumentException("blueRemaining must be >= 0: " + blueRemaining);
        }
        if (redRemaining < 0) {
            throw new IllegalArgumentException("redRemaining must be >= 0: " + redRemaining);
        }
    }

    /** A round in which both sides are wiped out / nothing survived. */
    public static Stats empty() {
        return new Stats(0, 0);
    }
}
