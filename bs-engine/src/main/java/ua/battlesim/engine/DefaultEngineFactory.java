package ua.battlesim.engine;

import java.util.Objects;

import ua.battlesim.api.CombatSystem;
import ua.battlesim.api.Engine;
import ua.battlesim.api.EngineFactory;
import ua.battlesim.api.MovementSystem;
import ua.battlesim.api.RoundSetup;
import ua.battlesim.api.SimConfig;
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

    private final CombatSystem combatSystem;
    private final MovementSystem movementSystem;

    /**
     * @param combatSystem    the combat rules to inject (non-null)
     * @param movementSystem  the movement rules to inject (non-null)
     * @throws NullPointerException if either system is null
     */
    public DefaultEngineFactory(CombatSystem combatSystem, MovementSystem movementSystem) {
        this.combatSystem = Objects.requireNonNull(combatSystem, "combatSystem");
        this.movementSystem = Objects.requireNonNull(movementSystem, "movementSystem");
    }

    /** @return the injected combat system */
    public CombatSystem combatSystem() {
        return combatSystem;
    }

    /** @return the injected movement system */
    public MovementSystem movementSystem() {
        return movementSystem;
    }

    @Override
    public Engine start(RoundSetup setup, SimConfig cfg) {
        Objects.requireNonNull(setup, "setup");
        Objects.requireNonNull(cfg, "cfg");
        return new DefaultEngine(setup, cfg, combatSystem, movementSystem);
    }
}