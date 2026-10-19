package ua.battlesim.api;

/**
 * Generates a fully wired {@link Terrain} for a deterministic seed.
 *
 * <p>Implementations live in bs-terrain (which wires bs-features and bs-nav);
 * bs-api only names the contract so same-layer modules never depend on each
 * other.
 *
 * <p>Determinism: {@code generate(seed)} must return an equivalent terrain
 * (same queries, same features) for the same seed; no wall clock, no
 * unseeded randomness.
 *
 * <p>Budget: generation of a 2000 m map under 1.5 s.
 */
public interface TerrainService {

    /**
     * @param seed the 64-bit deterministic seed of the round terrain
     * @return the generated terrain (non-null)
     */
    Terrain generate(long seed);
}
