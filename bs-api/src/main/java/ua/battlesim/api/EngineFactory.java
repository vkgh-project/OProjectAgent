package ua.battlesim.api;

/**
 * Creates engines for rounds.
 *
 * <p>Implementations live in the bs-engine module; bs-api only names the
 * contract so bs-session and bs-app can talk to the engine without importing it
 * (same-layer modules must not depend on each other).
 */
public interface EngineFactory {

    /**
     * @param setup the round to simulate (units are consumed by the engine)
     * @param cfg   the round configuration
     * @return a fresh engine in its initial (pre-first-tick) state
     */
    Engine start(RoundSetup setup, SimConfig cfg);
}
