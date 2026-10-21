package ua.battlesim.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.Engine;
import ua.battlesim.api.RenderSnapshot;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.Side;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.RoundSetup;

/**
 * Verifies that snapshots are immutable and do not reflect later mutations
 * of the underlying unit state.
 */
final class SnapshotImmutabilityTest {

    private SimConfig config() {
        return new SimConfig(2000.0, 0.1, 20, 100, 2, 1, 20, false);
    }

    private RoundSetup setup() {
        UnitData blue = new UnitData(0, Side.BLUE, UnitType.TANK, 100.0, 100.0, 0.0, 0.0, 100,
                UnitState.READY, 0, 0.0, -1);
        UnitData red = new UnitData(1, Side.RED, UnitType.TANK, 1900.0, 1900.0, Math.PI, Math.PI, 100,
                UnitState.READY, 0, 0.0, -1);
        return new RoundSetup(1, 42L, FakeTerrain.flat(), List.of(blue, red), new FakeWeather());
    }

    @Test
    void snapshotIsConsistentAfterFurtherTicks() {
        FakeCombatSystem combat = new FakeCombatSystem();
        FakeMovementSystem movement = new FakeMovementSystem();
        movement.setMoveOffset(5.0);

        Engine engine = new DefaultEngineFactory(cfg -> combat, cfg -> movement).start(setup(), config());

        // Tick once (still in ready phase since readyTicks=2)
        engine.tick();
        RenderSnapshot snap1 = engine.snapshot();
        int tick1 = snap1.tick();
        double unit0X = snap1.units().get(0).x();

        // Tick again
        engine.tick();
        RenderSnapshot snap2 = engine.snapshot();

        // snap1 should still have its original tick number
        assertEquals(tick1, snap1.tick());
        // The snapshot unit list should still show original values
        assertEquals(unit0X, snap1.units().get(0).x(), 1e-10);
    }

    @Test
    void snapshotUnitsListIsImmutable() {
        FakeCombatSystem combat = new FakeCombatSystem();
        FakeMovementSystem movement = new FakeMovementSystem();

        Engine engine = new DefaultEngineFactory(cfg -> combat, cfg -> movement).start(setup(), config());
        engine.tick();
        RenderSnapshot snap = engine.snapshot();

        // The units list should be unmodifiable
        assertThrows(UnsupportedOperationException.class, () ->
                snap.units().add(new ua.battlesim.api.UnitView(99, Side.BLUE, UnitType.TANK,
                        0, 0, 0, 0, 0, 0, 0, 0, 100, UnitState.READY, 0, -1)));
    }

    @Test
    void snapshotReflectsRoundNumber() {
        FakeCombatSystem combat = new FakeCombatSystem();
        FakeMovementSystem movement = new FakeMovementSystem();

        RoundSetup setup7 = new RoundSetup(7, 42L, FakeTerrain.flat(),
                List.of(new UnitData(0, Side.BLUE, UnitType.TANK, 0, 0, 0, 0, 100,
                        UnitState.READY, 0, 0.0, -1)),
                new FakeWeather());

        Engine engine = new DefaultEngineFactory(cfg -> combat, cfg -> movement).start(setup7, config());
        engine.tick();
        assertEquals(7, engine.snapshot().roundNumber());
    }

    @Test
    void weatherStateInSnapshot() {
        FakeWeather weather = new FakeWeather();
        FakeCombatSystem combat = new FakeCombatSystem();
        FakeMovementSystem movement = new FakeMovementSystem();

        UnitData blue = new UnitData(0, Side.BLUE, UnitType.TANK, 100.0, 100.0, 0.0, 0.0, 100,
                UnitState.READY, 0, 0.0, -1);
        UnitData red = new UnitData(1, Side.RED, UnitType.TANK, 1900.0, 1900.0, Math.PI, Math.PI, 100,
                UnitState.READY, 0, 0.0, -1);
        RoundSetup s = new RoundSetup(1, 42L, FakeTerrain.flat(), List.of(blue, red), weather);

        Engine engine = new DefaultEngineFactory(cfg -> combat, cfg -> movement).start(s, config());
        engine.tick();
        var snap = engine.snapshot();

        assertNotNull(snap.weather());
        assertEquals(0.0, snap.weather().rain());
        assertEquals(weather.lastSteppedTick(), 1);
    }
}