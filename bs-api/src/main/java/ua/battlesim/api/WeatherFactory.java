package ua.battlesim.api;

/**
 * Creates the deterministic per-round weather model.
 *
 * <p>Injected into the scenario factory (or used directly by a composition
 * root that builds weather itself), so the simulation core never depends on
 * a concrete weather implementation.
 *
 * <p>Determinism: {@code create(seed, config)} must return an equivalent
 * model for the same arguments; no wall clock and no unseeded randomness.
 * Each round gets a fresh model instance — models are mutable and must never
 * be shared between rounds or engines.
 */
@FunctionalInterface
public interface WeatherFactory {

    /**
     * @param seed    the deterministic weather seed (derived from the round
     *                seed by the caller)
     * @param config  the round configuration (the factory must honour
     *                {@code weatherEnabled} and {@code stepSeconds})
     * @return a fresh weather model (non-null)
     */
    WeatherModel create(long seed, SimConfig config);
}