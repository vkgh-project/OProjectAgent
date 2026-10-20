package ua.battlesim.app.internal;

import java.util.Objects;

import ua.battlesim.api.CombatSystem;
import ua.battlesim.api.EngineFactory;
import ua.battlesim.api.MovementSystem;
import ua.battlesim.api.ScenarioFactory;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.TerrainService;
import ua.battlesim.combat.StandardCombatSystem;
import ua.battlesim.engine.DefaultEngineFactory;
import ua.battlesim.movement.StandardMovementSystem;
import ua.battlesim.scenario.StandardScenarioFactory;
import ua.battlesim.session.DefaultSession;
import ua.battlesim.weather.SimpleWeatherModel;

/**
 * The composition root of the battle simulation: wires the real modules into
 * a single runnable graph without adding any new logic.
 *
 * <p>Wiring (all through the bs-api contracts):
 * <ul>
 *   <li>the terrain pipeline — bs-terrain + bs-features + bs-nav — behind the
 *       {@link TerrainService} contract ({@link AppTerrainService});</li>
 *   <li>{@link StandardCombatSystem} and {@link StandardMovementSystem}
 *       injected into the {@link DefaultEngineFactory};</li>
 *   <li>{@link StandardScenarioFactory} over the terrain service;</li>
 *   <li>{@link DefaultSession} over the scenario and engine factories (the
 *       session is the single owner of its engines);</li>
 *   <li>{@link SimpleWeatherModel} as the presentation-layer weather model
 *       (seeded from the master seed; stepped in lock-step with the session's
 *       ticks for the HUD weather line).</li>
 * </ul>
 *
 * <p>Determinism: the wiring is a pure function of {@code (config, seed)};
 * no wall clock, no unseeded randomness.
 *
 * <p>Thread-safety: the wiring itself is immutable; the session it creates
 * owns its worker thread.
 */
public final class AppWiring {

    private final SimConfig config;
    private final AppTerrainService terrainService;
    private final StandardCombatSystem combatSystem;
    private final StandardMovementSystem movementSystem;
    private final StandardScenarioFactory scenarioFactory;
    private final DefaultEngineFactory engineFactory;
    private final SimpleWeatherModel weatherDisplay;

    /**
     * @param config     the round configuration (non-null)
     * @param masterSeed the deterministic master seed of the session
     */
    public AppWiring(SimConfig config, long masterSeed) {
        this.config = Objects.requireNonNull(config, "config");
        this.terrainService = new AppTerrainService(config);
        this.combatSystem = new StandardCombatSystem();
        this.movementSystem = new StandardMovementSystem();
        this.scenarioFactory = new StandardScenarioFactory(terrainService, config);
        this.engineFactory = new DefaultEngineFactory(combatSystem, movementSystem);
        this.weatherDisplay = new SimpleWeatherModel(masterSeed, config.weatherEnabled());
    }

    /** @return the round configuration */
    public SimConfig config() {
        return config;
    }

    /** @return the terrain pipeline (bs-terrain + bs-features + bs-nav) behind the contract */
    public TerrainService terrainService() {
        return terrainService;
    }

    /** @return the combat rules */
    public CombatSystem combatSystem() {
        return combatSystem;
    }

    /** @return the movement rules */
    public MovementSystem movementSystem() {
        return movementSystem;
    }

    /** @return the scenario factory over the terrain service */
    public ScenarioFactory scenarioFactory() {
        return scenarioFactory;
    }

    /** @return the engine factory over combat and movement */
    public EngineFactory engineFactory() {
        return engineFactory;
    }

    /**
     * @return the seeded presentation-layer weather model (its
     *         {@code conditions()} feed the HUD weather line; it is stepped in
     *         lock-step with the session's ticks and never influences the
     *         simulation state)
     */
    public SimpleWeatherModel weatherDisplay() {
        return weatherDisplay;
    }

    /**
     * @param masterSeed the deterministic master seed of the session
     * @return a fresh {@link DefaultSession} (round 1 built synchronously,
     *         round 2 already generating on the worker thread)
     */
    public DefaultSession newSession(long masterSeed) {
        return new DefaultSession(scenarioFactory, engineFactory, config, masterSeed);
    }
}