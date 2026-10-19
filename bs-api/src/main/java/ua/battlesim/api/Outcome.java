package ua.battlesim.api;

/**
 * The result of a finished round.
 *
 * <p>Immutable and thread-safe (JVM enum).
 */
public enum Outcome {

    /** BLUE destroyed all RED units. */
    BLUE_WIN,

    /** RED destroyed all BLUE units. */
    RED_WIN,

    /** Both sides were wiped out on the same tick. */
    DRAW,

    /** The round ran to {@code maxRoundTicks} with both sides alive. */
    DRAW_TIME
}
