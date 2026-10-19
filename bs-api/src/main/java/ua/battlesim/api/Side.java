package ua.battlesim.api;

/**
 * The two opposing sides of the battle.
 *
 * <p>BLUE is the northern side (small z), RED the southern side (large z).
 *
 * <p>Immutable and thread-safe (JVM enum).
 */
public enum Side {

    /** The northern side. */
    BLUE,

    /** The southern side. */
    RED
}
