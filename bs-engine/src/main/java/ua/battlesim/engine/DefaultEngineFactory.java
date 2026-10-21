package ua.battlesim.engine;

import java.util.Objects;

import ua.battlesim.api.CombatSystem;
import ua.battlesim.api.Engine;
import ua.battlesim.api.EngineFactory;
import ua.battlesim.api.MovementSystem;
import ua.battlesim.api.RoundSetup;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.SystemProvider;
import ua.battlesim.engine.internal.DefaultEngine;

/**
 * Default factory for creating {@link Engine} instances (bs-engine module).
 *
 * <p>Implements the {@link EngineFactory} contract from bs-api so that
 * bs-session and bs-app can create engines without importing bs-engine
 * directly (same-layer dependency rule).
 *
 * <p>Thread-safety: this class is immutable after construction; safe to share
 * across threads.
 *
 * <p>Determinism: the produced engines are deterministic functions of their
 * inputs; no wall clock, no unseeded randomness.
 */
public final class DefaultEngineFactory implements EngineFactory {

    private final SystemProvider<CombatSystem> combatProvider;
    private final SystemProvider<MovementSystem> movementProvider;

    /**
     * @param combatProvider    creates each engine's combat system from the
     *                          round configuration (non-null)
     * @param movementProvider  creates each engine's movement system from the
     *                          round configuration (non-null)
     * @throws NullPointerException if either provider is null
     */
    public DefaultEngineFactory(SystemProvider<CombatSystem> combatProvider,
            SystemProvider<MovementSystem> movementProvider) {
        this.combatProvider = Objects.requireNonNull(combatProvider, "combatProvider");
        this.movementProvider = Objects.requireNonNull(movementProvider, "movementProvider");
    }

    /** @return the combat system provider */
    public SystemProvider<CombatSystem> combatProvider() {
        return combatProvider;
    }

    /** @return the movement system provider */
    public SystemProvider<MovementSystem> movementProvider() {
        return movementProvider;
    }

    /**
     * Starts a fresh engine for the given round. The engine owns brand-new
     * combat and movement systems (created by the providers from the round
     * configuration), its own copy of the setup's units and the setup's own
     * weather model — two engines started for two (possibly identical)
     * round setups never share mutable state.
     */
    @Override
    public Engine start(RoundSetup setup, SimConfig cfg) {
        Objects.requireNonNull(setup, "setup");
        Objects.requireNonNull(cfg, "cfg");
        CombatSystem combat = Objects.requireNonNull(combatProvider.create(cfg), "combat system");
        MovementSystem movement = Objects.requireNonNull(movementProvider.create(cfg), "movement system");
        return new DefaultEngine(setup, cfg, combat, movement);
    }
}