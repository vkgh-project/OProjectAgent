package ua.battlesim.gfx.units.internal;

/**
 * The role of a unit model piece. Drives both per-vertex animation (which
 * parts spin, aim or swing) and shading (which parts take the side paint).
 *
 * <p>Internal to {@code bs-gfx-units}; not part of the named public API
 * (that is {@code ua.battlesim.gfx.units.UnitRenderer}).
 */
public enum Role {
    /** Vehicle / infantry body — painted in the side colour. */
    HULL,
    /** Track body — dark, with a scrolling tread pattern. */
    TRACK,
    /** Road / drive wheel — dark, rotates with the odometer. */
    WHEEL,
    /** Turret — dark, rotates with the gun aim. */
    TURRET,
    /** Main gun barrel — dark, aims and recoils. */
    GUN,
    /** Infantry torso — painted in the side colour. */
    TORSO,
    /** Infantry vest — painted in the side colour (brighter). */
    VEST,
    /** Soldier head / helmet — painted in the side colour. */
    HEAD,
    /** Infantry leg — dark, swings with the walk cycle. */
    LEG,
    /** Soft blob shadow under the unit (translucent). */
    SHADOW,
    /** Selection ring on the ground (drawn only when selected). */
    MARKER
}
