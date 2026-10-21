package ua.battlesim.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.Attack;
import ua.battlesim.api.Engine;
import ua.battlesim.api.RoundSetup;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.Side;
import ua.battlesim.api.SimEvent;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitDestroyedEvent;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;

/**
 * Engine isolation: an engine deep-copies its round's units and owns its own
 * combat/movement systems, so two engines (even over the same setup) never
 * share mutable state; a unit destroyed by several attackers this tick
 * produces exactly one destruction event with the lowest attacker as killer.
 */
final class EngineIsolationTest {

    private static final int READY_TICKS = 1;

    private static SimConfig config() {
        return new SimConfig(2000.0, 0.1, 20, 100, READY_TICKS, 1, 20, false);
    }

    private static RoundSetup setupWithThreeUnits() {
        UnitData blue0 = new UnitData(0, Side.BLUE, UnitType.TANK, 100.0, 100.0, 0.0, 0.0,
                100, UnitState.READY, 0, 0.0, -1);
        UnitData red1 = new UnitData(1, Side.RED, UnitType.TANK, 200.0, 100.0, 0.0, 0.0,
                100, UnitState.READY, 0, 0.0, -1);
        UnitData blue2 = new UnitData(2, Side.BLUE, UnitType.TANK, 300.0, 100.0, 0.0, 0.0,
                100, UnitState.READY, 0, 0.0, -1);
        return new RoundSetup(1, 42L, FakeTerrain.flat(), List.of(blue0, red1, blue2),
                new FakeWeather());
    }

    private static FakeCombatSystem killingCombat() {
        FakeCombatSystem combat = new FakeCombatSystem();
        combat.setNextAttacks(List.of(new Attack(0, 1, 100, true)));
        return combat;
    }

    private static Engine freshEngine() {
        return new DefaultEngineFactory(cfg -> killingCombat(), cfg -> new FakeMovementSystem())
                .start(setupWithThreeUnits(), config());
    }

    @Test
    void twoKillsOfOneVictimProduceExactlyOneDestructionEvent() {
        FakeCombatSystem combat = new FakeCombatSystem();
        // Both blue tanks kill red unit 1 on the same tick.
        combat.setNextAttacks(List.of(
                new Attack(2, 1, 60, true),
                new Attack(0, 1, 40, true)));
        FakeMovementSystem movement = new FakeMovementSystem();
        Engine engine = new DefaultEngineFactory(cfg -> combat, cfg -> movement)
                .start(setupWithThreeUnits(), config());

        for (int i = 0; i < READY_TICKS; i++) {
            engine.tick();
        }
        engine.tick();

        List<SimEvent> events = engine.drainEvents();
        long attacks = events.stream()
                .filter(e -> e instanceof ua.battlesim.api.AttackEvent).count();
        long destructions = events.stream()
                .filter(e -> e instanceof UnitDestroyedEvent).count();
        assertEquals(2, attacks, "one AttackEvent per shot: " + events);
        assertEquals(1, destructions, "exactly one UnitDestroyedEvent per victim: " + events);
        UnitDestroyedEvent d = (UnitDestroyedEvent) events.stream()
                .filter(e -> e instanceof UnitDestroyedEvent).findFirst().orElseThrow();
        assertEquals(1, d.victimId());
        assertEquals(0, d.killerId(), "the killer must be the lowest attacker id");
        assertEquals(Side.BLUE, d.killerSide());
    }

    @Test
    void twoEnginesOverTheSameSetupStayIndependent() {
        RoundSetup shared = setupWithThreeUnits();

        // Engine A kills red unit 1 and ends; engine B keeps going.
        Engine engineA = new DefaultEngineFactory(cfg -> killingCombat(),
                cfg -> new FakeMovementSystem()).start(shared, config());
        Engine engineB = new DefaultEngineFactory(cfg -> new FakeCombatSystem(),
                cfg -> new FakeMovementSystem()).start(shared, config());

        for (int i = 0; i < READY_TICKS + 1 && !engineA.finished(); i++) {
            engineA.tick();
        }
        assertTrue(engineA.finished());

        engineB.tick();
        engineB.tick();
        assertFalse(engineB.finished(), "engine B must still be running");
        boolean redAlive = engineB.snapshot().units().stream()
                .anyMatch(u -> u.id() == 1 && u.state() != UnitState.DESTROYED);
        assertTrue(redAlive, "engine B's red unit must be alive despite engine A's kill");
        assertTrue(shared.units().stream().allMatch(u -> u.state() != UnitState.DESTROYED),
                "the round setup's units must never be mutated by the engines");
    }

    @Test
    void interleavedEnginesMatchSequentialRuns() {
        // Sequential reference: run one engine to completion.
        Engine reference = freshEngine();
        while (!reference.finished()) {
            reference.tick();
        }
        long referenceHash = reference.stateHash();

        // Interleaved: two fresh engines on two fresh setups, ticked one by
        // one, must each end in the same state.
        Engine a = freshEngine();
        Engine b = freshEngine();
        int guard = 0;
        while ((!a.finished() || !b.finished()) && guard++ < 200) {
            if (!a.finished()) {
                a.tick();
            }
            if (!b.finished()) {
                b.tick();
            }
        }
        assertTrue(a.finished() && b.finished());
        assertEquals(referenceHash, a.stateHash(), "engine A must match the sequential run");
        assertEquals(referenceHash, b.stateHash(), "engine B must match the sequential run");
    }
}