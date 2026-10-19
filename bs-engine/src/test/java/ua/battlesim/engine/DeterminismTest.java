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
 * Verifies determinism: two identical runs produce identical state hashes
 * at every tick, and different runs produce (almost certainly) different hashes.
 */
final class DeterminismTest {

    private SimConfig config() {
        return new SimConfig(2000.0, 0.1, 20, 100, 2, 1, 20, false);
    }

    private RoundSetup standardSetup() {
        UnitData blue = new UnitData(0, Side.BLUE, UnitType.TANK, 100.0, 100.0, 0.0, 0.0, 100,
                UnitState.READY, 0, 0.0, -1);
        UnitData red = new UnitData(1, Side.RED, UnitType.TANK, 1900.0, 1900.0, Math.PI, Math.PI, 100,
                UnitState.READY, 0, 0.0, -1);
        return new RoundSetup(1, 42L, FakeTerrain.flat(), List.of(blue, red), new FakeWeather());
    }

    @Test
    void identicalRunsProduceIdenticalHashes() {
        int ticks = 10;

        long[] hashes1 = runTicks(ticks);
        long[] hashes2 = runTicks(ticks);

        for (int i = 0; i < ticks; i++) {
            assertEquals(hashes1[i], hashes2[i],
                    "Hash mismatch at tick " + (i + 1));
        }
    }

    @Test
    void differentSeedsProduceDifferentHashes() {
        int ticks = 5;

        UnitData blue1 = new UnitData(0, Side.BLUE, UnitType.TANK, 100.0, 100.0, 0.0, 0.0, 100,
                UnitState.READY, 0, 0.0, -1);
        UnitData red1 = new UnitData(1, Side.RED, UnitType.TANK, 1900.0, 1900.0, Math.PI, Math.PI, 100,
                UnitState.READY, 0, 0.0, -1);
        RoundSetup s1 = new RoundSetup(1, 42L, FakeTerrain.flat(), List.of(blue1, red1), new FakeWeather());

        // Different positions
        UnitData blue2 = new UnitData(0, Side.BLUE, UnitType.TANK, 500.0, 500.0, 0.0, 0.0, 100,
                UnitState.READY, 0, 0.0, -1);
        UnitData red2 = new UnitData(1, Side.RED, UnitType.TANK, 1500.0, 1500.0, Math.PI, Math.PI, 100,
                UnitState.READY, 0, 0.0, -1);
        RoundSetup s2 = new RoundSetup(1, 99L, FakeTerrain.flat(), List.of(blue2, red2), new FakeWeather());

        FakeCombatSystem c1 = new FakeCombatSystem();
        FakeMovementSystem m1 = new FakeMovementSystem();
        m1.setMoveOffset(3.0);
        Engine e1 = new DefaultEngineFactory(c1, m1).start(s1, config());

        FakeCombatSystem c2 = new FakeCombatSystem();
        FakeMovementSystem m2 = new FakeMovementSystem();
        m2.setMoveOffset(3.0);
        Engine e2 = new DefaultEngineFactory(c2, m2).start(s2, config());

        for (int i = 0; i < ticks; i++) {
            e1.tick();
            e2.tick();
        }

        assertNotEquals(e1.stateHash(), e2.stateHash(),
                "Different initial positions should produce different state hashes");
    }

    @Test
    void hashChangesAfterMovement() {
        FakeCombatSystem combat = new FakeCombatSystem();
        FakeMovementSystem movement = new FakeMovementSystem();
        movement.setMoveOffset(10.0);

        Engine engine = new DefaultEngineFactory(combat, movement).start(standardSetup(), config());

        long hashBefore = engine.stateHash();

        // Advance past ready phase so movement kicks in
        for (int i = 0; i <= 2; i++) engine.tick();

        long hashAfter = engine.stateHash();
        assertNotEquals(hashBefore, hashAfter,
                "Hash should change after units have moved");
    }

    private long[] runTicks(int nTicks) {
        FakeCombatSystem combat = new FakeCombatSystem();
        FakeMovementSystem movement = new FakeMovementSystem();
        movement.setMoveOffset(2.0);
        Engine engine = new DefaultEngineFactory(combat, movement).start(standardSetup(), config());

        long[] hashes = new long[nTicks];
        for (int i = 0; i < nTicks; i++) {
            engine.tick();
            hashes[i] = engine.stateHash();
        }
        return hashes;
    }
}