package ua.battlesim.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.Attack;
import ua.battlesim.api.Engine;
import ua.battlesim.api.Outcome;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.Side;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitDestroyedEvent;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.RoundSetup;
import ua.battlesim.api.SimEvent;

/**
 * Verifies end-condition rules:
 * - BLUE all destroyed -> RED_WIN
 * - RED all destroyed -> BLUE_WIN
 * - Both destroyed same tick -> DRAW
 * - maxRoundTicks reached -> DRAW_TIME
 */
final class EndRulesTest {

    private static final int READY_TICKS = 2;
    private static final int MAX_TICKS = 5;

    private SimConfig config() {
        return new SimConfig(2000.0, 0.1, 20, MAX_TICKS, READY_TICKS, 1, 20, false);
    }

    private RoundSetup standardSetup() {
        UnitData blue = new UnitData(0, Side.BLUE, UnitType.TANK, 100.0, 100.0, 0.0, 0.0, 100,
                UnitState.READY, 0, 0.0, -1);
        UnitData red = new UnitData(1, Side.RED, UnitType.TANK, 1900.0, 1900.0, Math.PI, Math.PI, 100,
                UnitState.READY, 0, 0.0, -1);
        return new RoundSetup(1, 42L, FakeTerrain.flat(), List.of(blue, red), new FakeWeather());
    }

    @Test
    void blueWipedOutRedWins() {
        FakeCombatSystem combat = new FakeCombatSystem();
        // Kill the blue unit (id 0)
        combat.setNextAttacks(List.of(new Attack(1, 0, 100, true)));

        FakeMovementSystem movement = new FakeMovementSystem();
        Engine engine = new DefaultEngineFactory(combat, movement).start(standardSetup(), config());

        // Advance past ready phase
        for (int i = 0; i < READY_TICKS; i++) engine.tick();
        // On next tick, combat kills blue
        engine.tick();

        assertTrue(engine.finished());
        assertEquals(Outcome.RED_WIN, engine.outcome());

        // Verify events include the destruction
        List<SimEvent> events = engine.drainEvents();
        assertTrue(events.stream().anyMatch(e -> e instanceof UnitDestroyedEvent
                && ((UnitDestroyedEvent) e).victimId() == 0
                && ((UnitDestroyedEvent) e).killerId() == 1
                && ((UnitDestroyedEvent) e).killerSide() == Side.RED));
    }

    @Test
    void redWipedOutBlueWins() {
        FakeCombatSystem combat = new FakeCombatSystem();
        // Kill the red unit (id 1)
        combat.setNextAttacks(List.of(new Attack(0, 1, 100, true)));

        FakeMovementSystem movement = new FakeMovementSystem();
        Engine engine = new DefaultEngineFactory(combat, movement).start(standardSetup(), config());

        for (int i = 0; i < READY_TICKS; i++) engine.tick();
        engine.tick();

        assertTrue(engine.finished());
        assertEquals(Outcome.BLUE_WIN, engine.outcome());
    }

    @Test
    void bothWipedSameTickIsDraw() {
        FakeCombatSystem combat = new FakeCombatSystem();
        // Both kill each other on the same tick
        combat.setNextAttacks(List.of(
                new Attack(0, 1, 100, true),
                new Attack(1, 0, 100, true)));

        FakeMovementSystem movement = new FakeMovementSystem();
        Engine engine = new DefaultEngineFactory(combat, movement).start(standardSetup(), config());

        for (int i = 0; i < READY_TICKS; i++) engine.tick();
        engine.tick();

        assertTrue(engine.finished());
        assertEquals(Outcome.DRAW, engine.outcome());
    }

    @Test
    void maxTicksReachedIsDrawTime() {
        FakeCombatSystem combat = new FakeCombatSystem();
        FakeMovementSystem movement = new FakeMovementSystem();
        Engine engine = new DefaultEngineFactory(combat, movement).start(standardSetup(), config());

        // Advance to maxRoundTicks
        for (int i = 0; i < MAX_TICKS; i++) engine.tick();

        assertTrue(engine.finished());
        assertEquals(Outcome.DRAW_TIME, engine.outcome());
    }

    @Test
    void cannotTickAfterFinished() {
        FakeCombatSystem combat = new FakeCombatSystem();
        combat.setNextAttacks(List.of(new Attack(1, 0, 100, true)));

        FakeMovementSystem movement = new FakeMovementSystem();
        Engine engine = new DefaultEngineFactory(combat, movement).start(standardSetup(), config());

        for (int i = 0; i < READY_TICKS; i++) engine.tick();
        engine.tick(); // this kills blue, round ends

        assertTrue(engine.finished());
        assertThrows(IllegalStateException.class, engine::tick);
    }

    @Test
    void outcomeThrowsWhenNotFinished() {
        FakeCombatSystem combat = new FakeCombatSystem();
        FakeMovementSystem movement = new FakeMovementSystem();
        Engine engine = new DefaultEngineFactory(combat, movement).start(standardSetup(), config());
        engine.tick(); // still in ready phase
        assertFalse(engine.finished());
        assertThrows(IllegalStateException.class, engine::outcome);
    }
}