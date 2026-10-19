package ua.battlesim.api;

import java.util.List;

/**
 * The combat rules, injected by bs-app into the engine (the engine never
 * imports bs-combat; same-layer modules talk through this contract).
 *
 * <p>Threading: called from the single simulation worker thread, after the
 * movement step of the same tick.
 *
 * <p>Determinism: units must be processed in ascending id order; the result
 * depends only on the given arguments.
 */
public interface CombatSystem {

    /**
     * Resolves all attacks of the given tick.
     *
     * @param tick  the current tick (time = tick x stepSeconds)
     * @param units all units of the round; mutable, owned by the caller
     * @param t     the round terrain
     * @param w     the round weather model (non-null, even when weather is
     *              disabled)
     * @return the attacks resolved this tick, in a deterministic order
     */
    List<Attack> resolve(int tick, List<UnitData> units, Terrain t, WeatherModel w);
}
