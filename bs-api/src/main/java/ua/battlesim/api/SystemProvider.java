package ua.battlesim.api;

/**
 * Creates one simulation system (combat or movement) from the round
 * configuration.
 *
 * <p>Used by engine factories so that <em>every engine owns its own fresh,
 * mutable system instance</em> — systems carry per-unit state and must never
 * be shared between engines or rounds.
 *
 * <p>Determinism: {@code create(config)} must return equivalent systems for
 * the same configuration; no unseeded randomness.
 *
 * @param <T> the system type (e.g. {@link CombatSystem} or {@link MovementSystem})
 */
@FunctionalInterface
public interface SystemProvider<T> {

    /**
     * @param config the round configuration (non-null)
     * @return a fresh system instance (non-null)
     */
    T create(SimConfig config);
}