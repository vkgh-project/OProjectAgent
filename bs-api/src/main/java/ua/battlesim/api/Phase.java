package ua.battlesim.api;

/**
 * The lifecycle phase of a battle session as seen by the presentation layer.
 *
 * <p>Immutable and thread-safe (JVM enum).
 */
public enum Phase {
    /**
     * A round is in progress and its engine is being advanced.
     */
    RUNNING,
    /**
     * The round has finished and the next round is being generated on the
     * worker thread; no engine output is valid yet.
     */
    INTERMISSION,
    /**
     * The intermission has elapsed but the next round is not ready yet (still
     * generating, possibly after a failed attempt); the session is waiting
     * for the worker thread.
     */
    GENERATING,
    /**
     * A decisive outcome has been reached; the session has no more rounds.
     */
    FINISHED
}
