package ua.battlesim.api;

/**
 * The deterministic per-round weather model, injected by bs-app into the
 * engine (the engine never imports bs-weather).
 *
 * <p>Threading: {@link #step} is called from the single simulation worker
 * thread before movement and combat of that tick; {@link #state()} and the
 * factor methods may be read once the tick is published via the engine
 * snapshot.
 *
 * <p>Units: tick in ticks (time = tick x stepSeconds); wind direction and
 * speed as in {@link WeatherState}; the speed and combat factors are
 * dimensionless multipliers; {@link #visibilityMetres()} is a distance in
 * metres (finite, strictly positive).
 *
 * <p>Implementations are injected per round through a
 * {@link WeatherFactory}; the same tick sequence must produce the same
 * states — no wall clock, no unseeded randomness.
 */
public interface WeatherModel {

    /**
     * Advances the weather to the given tick; deterministic and repeatable
     * per tick.
     *
     * @param tick the current tick (time = tick x stepSeconds)
     */
    void step(int tick);

    /** @return the current immutable weather state */
    WeatherState state();

    /**
     * @param dirX the unit's movement direction in the (x, z) plane (x east)
     * @param dirZ the unit's movement direction in the (x, z) plane (z south)
     * @return the movement speed multiplier in {@code [0, 1]} for that
     *         direction
     */
    double speedFactor(double dirX, double dirZ);

    /**
     * @return the current visibility distance in metres: finite and strictly
     *         positive (larger = clearer)
     */
    double visibilityMetres();

    /** @return the combat accuracy multiplier in {@code [0, 1]} (1 = no weather penalty) */
    double combatFactor();
}
