package ua.battlesim.weather.internal;

/**
 * The exclusive precipitation regimes of the weather model.
 *
 * <p>Exactly one regime is the active target at any moment: {@code CLEAR},
 * {@code RAIN}, {@code HEAVY_RAIN} or {@code SNOW}. Intensity channels
 * transition between regimes by exponential blending, so a briefly fading
 * old channel may be non-zero only right after a regime change; it decays to
 * exactly zero within a few hundred seconds.
 */
public enum Precip {
    CLEAR,
    RAIN,
    HEAVY_RAIN,
    SNOW
}
