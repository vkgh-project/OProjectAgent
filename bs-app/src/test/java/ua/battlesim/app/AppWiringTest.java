package ua.battlesim.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.Engine;
import ua.battlesim.api.RoundSetup;
import ua.battlesim.api.Side;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitData;
import ua.battlesim.app.internal.AppWiring;

/**
 * Verifies that the composition root wires every core module into a working
 * graph: the terrain pipeline produces a complete {@link Terrain}, the
 * scenario factory builds both sides, the engine advances, and the
 * presentation weather model reports conditions.
 */
final class AppWiringTest {

    private static final SimConfig CFG = new SimConfig(600.0, 0.1, 20, 300, 20, 1, 3, true);

    @Test
    void wiresEveryCoreModule() {
        AppWiring wiring = new AppWiring(CFG, 99);
        assertNotNull(wiring.terrainService(), "terrain pipeline behind the contract");
        assertNotNull(wiring.combatSystem(), "combat rules");
        assertNotNull(wiring.movementSystem(), "movement rules");
        assertNotNull(wiring.scenarioFactory(), "scenario factory");
        assertNotNull(wiring.engineFactory(), "engine factory");
        assertNotNull(wiring.weatherDisplay(), "presentation weather model");
        assertFalse(wiring.weatherDisplay().conditions().isEmpty(),
                "the weather model must report at least one condition");
        assertTrue(wiring.weatherDisplay().visibility() > 0.0, "visibility must be positive");
        assertEquals(CFG, wiring.config());
    }

    @Test
    void terrainPipelineProducesCompleteTerrain() {
        AppWiring wiring = new AppWiring(CFG, 99);
        Terrain terrain = wiring.terrainService().generate(1234);
        assertNotNull(terrain, "generated terrain");
        assertEquals(600.0, terrain.size(), "map size comes from the config");
        assertNotNull(terrain.water(), "water field");
        assertNotNull(terrain.nav(), "navigation grid");
        assertNotNull(terrain.los(), "line of sight");
        assertNotNull(terrain.planner(), "path planner");
        assertNotNull(terrain.roads(), "roads");
        assertNotNull(terrain.bridges(), "bridges");
        assertNotNull(terrain.trees(), "trees");
        assertNotNull(terrain.rocks(), "rocks");
        assertEquals(3, terrain.sunDirection().length, "sun direction is a 3-D vector");
        assertTrue(terrain.shadeAt(10.0, 10.0) >= 0.0 && terrain.shadeAt(10.0, 10.0) <= 1.0,
                "shading is in [0, 1]");
    }

    @Test
    void scenarioCreatesBothSidesAndEngineAdvances() {
        AppWiring wiring = new AppWiring(CFG, 99);
        RoundSetup setup = wiring.scenarioFactory().create(99, 1);
        assertEquals(1, setup.number(), "round number is preserved");
        assertNotNull(setup.weather(), "the round carries its weather model");
        int blue = 0;
        int red = 0;
        for (UnitData unit : setup.units()) {
            if (unit.side() == Side.BLUE) {
                blue++;
            } else {
                red++;
            }
        }
        assertTrue(blue >= 1, "BLUE must have at least minUnits");
        assertTrue(red >= 1, "RED must have at least minUnits");
        assertTrue(blue <= 3 && red <= 3, "unit scale must respect the config");

        Engine engine = wiring.engineFactory().start(setup, wiring.config());
        assertFalse(engine.finished(), "a fresh engine must not be finished");
        for (int i = 0; i < 100 && !engine.finished(); i++) {
            engine.tick();
        }
        assertNotNull(engine.snapshot(), "the engine must expose a snapshot");
        assertNotNull(engine.snapshot().weather(), "the snapshot must carry weather");
        assertTrue(engine.snapshot().units().size() >= 2, "the snapshot must carry both sides' units");
    }
}