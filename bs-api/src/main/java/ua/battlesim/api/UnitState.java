package ua.battlesim.api;

/**
 * The behavioural state of a unit within the current tick.
 *
 * <p>Immutable and thread-safe (JVM enum).
 */
public enum UnitState {

    /** Idle, ready to move or fire. */
    READY,

    /** Following a planned path. */
    MOVING,

    /** Firing at a visible enemy. */
    ATTACKING,

    /** Deliberately holding position. */
    WAITING,

    /** No longer acts; health is 0. */
    DESTROYED
}
