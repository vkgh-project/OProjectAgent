package ua.battlesim.api;

/**
 * The unit classes available in the simulation.
 *
 * <p>Per-type physical and combat parameters (radius, speed, turn rate, eye
 * height, range, damage table) live in {@link UnitInfo}.
 *
 * <p>Immutable and thread-safe (JVM enum).
 */
public enum UnitType {

    /** Armoured tracked fighting vehicle. */
    TANK,

    /** Dismounted infantry squad. */
    INFANTRY,

    /** Armoured personnel carrier with a dismounted infantry squad. */
    APC_INFANTRY
}
