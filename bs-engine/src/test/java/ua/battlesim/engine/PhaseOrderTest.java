package ua.battlesim.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.Engine;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.Side;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.RoundSetup;

/**
 * Verifies the per-tick phase order:
 * 1. tick advances
 * 2. prev values are saved
 * 3. weather.step is called
 * 4. during readyTicks: combat and movement are NOT called
 * 5. after readyTicks: combat is called before movement
 */
final class PhaseOrderTest {

    private static final int READY_TICKS = 3;

    private SimConfig config() {
        return new SimConfig(2000.0, 0.1, 20, 100, READY_TICKS, 1, 20, false);
    }

    private RoundSetup setup() {
        UnitData blue = new UnitData(0, Side.BLUE, UnitType.TANK, 100.0, 100.0, 0.0, 0.0, 100,
                UnitState.READY, 0, 0.0, -1);
        UnitData red = new UnitData(1, Side.RED, UnitType.TANK, 1900.0, 1900.0, Math.PI, Math.PI, 100,
                UnitState.READY, 0, 0.0, -1);
        return new RoundSetup(1, 42L, FakeTerrain.flat(), List.of(blue, red), new FakeWeather());
    }

    @Test
    void combatAndMovementSkippedDuringReadyPhase() {
        FakeCombatSystem combat = new FakeCombatSystem();
        FakeMovementSystem movement = new FakeMovementSystem();
        FakeWeather weather = new FakeWeather();

        RoundSetup s = setupWith(weather);
        Engine engine = new DefaultEngineFactory(cfg -> combat, cfg -> movement).start(s, config());

        // Run through all ready ticks
        for (int i = 0; i < READY_TICKS; i++) {
            engine.tick();
        }

        // Weather was stepped every tick
        assertEquals(READY_TICKS, weather.stepCount());
        // But combat and movement were NOT called
        assertTrue(combat.callLog.isEmpty(), "Combat should not be called during ready phase");
        assertTrue(movement.callLog.isEmpty(), "Movement should not be called during ready phase");
    }

    @Test
    void combatBeforeMovementAfterReadyPhase() {
        FakeCombatSystem combat = new FakeCombatSystem();
        FakeMovementSystem movement = new FakeMovementSystem();

        Engine engine = new DefaultEngineFactory(cfg -> combat, cfg -> movement).start(setup(), config());

        // Advance past ready phase
        for (int i = 0; i < READY_TICKS; i++) {
            engine.tick();
        }
        // This tick should trigger combat then movement
        engine.tick();

        assertEquals(1, combat.callLog.size());
        assertEquals(1, movement.callLog.size());
        assertEquals("combat:" + (READY_TICKS + 1), combat.callLog.get(0));
        assertEquals("movement:" + (READY_TICKS + 1), movement.callLog.get(0));
    }

    @Test
    void tickCounterAdvancesCorrectly() {
        FakeCombatSystem combat = new FakeCombatSystem();
        FakeMovementSystem movement = new FakeMovementSystem();

        Engine engine = new DefaultEngineFactory(cfg -> combat, cfg -> movement).start(setup(), config());
        assertFalse(engine.finished());

        engine.tick();
        var snap = engine.snapshot();
        assertEquals(1, snap.tick());
        assertEquals(0.1, snap.timeSeconds(), 1e-10);

        engine.tick();
        snap = engine.snapshot();
        assertEquals(2, snap.tick());
        assertEquals(0.2, snap.timeSeconds(), 1e-10);
    }

    @Test
    void prevValuesReflectedInSnapshot() {
        FakeCombatSystem combat = new FakeCombatSystem();
        FakeMovementSystem movement = new FakeMovementSystem();
        movement.setMoveOffset(10.0);

        Engine engine = new DefaultEngineFactory(cfg -> combat, cfg -> movement).start(setup(), config());

        // Advance past ready phase so movement happens
        for (int i = 0; i <= READY_TICKS; i++) {
            engine.tick();
        }

        var snap = engine.snapshot();
        // Unit 0 started at x=100.0. After one movement step, x=110.0.
        // prevX should be 100.0.
        var unit0 = snap.units().stream().filter(u -> u.id() == 0).findFirst().orElseThrow();
        assertEquals(110.0, unit0.x(), 1e-10);
        assertEquals(100.0, unit0.prevX(), 1e-10);
    }

    private RoundSetup setupWith(FakeWeather weather) {
        UnitData blue = new UnitData(0, Side.BLUE, UnitType.TANK, 100.0, 100.0, 0.0, 0.0, 100,
                UnitState.READY, 0, 0.0, -1);
        UnitData red = new UnitData(1, Side.RED, UnitType.TANK, 1900.0, 1900.0, Math.PI, Math.PI, 100,
                UnitState.READY, 0, 0.0, -1);
        return new RoundSetup(1, 42L, FakeTerrain.flat(), List.of(blue, red), weather);
    }
}