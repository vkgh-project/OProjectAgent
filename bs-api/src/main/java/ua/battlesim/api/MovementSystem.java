package ua.battlesim.api;

import java.util.List;

/**
 * The movement rules, injected by bs-app into the engine (the engine never
 * imports bs-movement; same-layer modules talk through this contract).
 *
 * <p>Threading: called from the single simulation worker thread, before the
 * combat step of the same tick.
 *
 * <p>Determinism: units must be processed in ascending id order; the result
 * depends only on the given arguments.
 */
public interface MovementSystem {

    /**
     * Advances every unit's movement by one tick, mutating the given units in
     * place.
     *
     * @param tick  the current tick (time = tick x stepSeconds)
     * @param units all units of the round; mutable, owned by the caller
     * @param t     the round terrain
     * @param w     the round weather model (non-null, even when weather is
     *              disabled)
     */
    void step(int tick, List<UnitData> units, Terrain t, WeatherModel w);
}
