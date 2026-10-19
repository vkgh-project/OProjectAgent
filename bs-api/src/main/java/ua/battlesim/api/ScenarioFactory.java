package ua.battlesim.api;

/**
 * Builds the initial state of a round from a master seed and a round number.
 *
 * <p>Determinism: {@code create(masterSeed, roundNumber)} must return the same
 * setup (same terrain seed, same units) for the same arguments; no wall clock
 * and no unseeded randomness.
 */
public interface ScenarioFactory {

    /**
     * @param masterSeed  the deterministic master seed of the session
     * @param roundNumber the 1-based round number
     * @return the fully wired round (terrain, units, weather)
     */
    RoundSetup create(long masterSeed, int roundNumber);
}
