package ua.battlesim.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.Side;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;

/** A unit with a target is ATTACKING and turns its aim toward it. */
class AimAndStateTest {

    @Test
    void attackerIsAttackingAndAimTurnsTowardTheTarget() {
        UnitData blue = CombatKit.unit(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        UnitData red = CombatKit.unit(2, Side.RED, UnitType.INFANTRY, 0.0, 100.0);
        StandardCombatSystem combat = new StandardCombatSystem();

        for (int tick = 0; tick < 5; tick++) {
            combat.resolve(tick, List.of(blue, red), CombatKit.clearTerrain(), FakeWeather.clear());
        }
        assertEquals(UnitState.ATTACKING, blue.state(), "a unit with a target is ATTACKING");
        // five ticks x 3 rad/s x 0.1 s = 1.5 rad, target direction is pi/2
        assertEquals(5 * 3.0 * 0.1, blue.aim(), 1e-9);
    }

    @Test
    void aimSnapsToTheTargetDirectionOnceCloseEnough() {
        UnitData blue = CombatKit.unit(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        UnitData red = CombatKit.unit(2, Side.RED, UnitType.INFANTRY, 100.0, 0.0);
        blue.setAim(0.25); // 0.25 rad away: within one tank step (0.3 rad)

        new StandardCombatSystem()
                .resolve(0, List.of(blue, red), CombatKit.clearTerrain(), FakeWeather.clear());

        assertEquals(0.0, blue.aim(), 0.0, "within one step the aim snaps to the target");
    }

    @Test
    void nonTanksAimAtFourRadiansPerSecond() {
        UnitData blue = CombatKit.unit(1, Side.BLUE, UnitType.INFANTRY, 0.0, 0.0);
        UnitData red = CombatKit.unit(2, Side.RED, UnitType.TANK, 0.0, 100.0);

        new StandardCombatSystem()
                .resolve(0, List.of(blue, red), CombatKit.clearTerrain(), FakeWeather.clear());

        assertEquals(4.0 * 0.1, blue.aim(), 1e-9, "infantry aim step is 4 rad/s x 0.1 s");
    }

    @Test
    void aimMovesAlongTheShortestArc() {
        UnitData blue = CombatKit.unit(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        UnitData red = CombatKit.unit(2, Side.RED, UnitType.INFANTRY, 100.0, 0.0);
        blue.setAim(3.0); // target direction 0: one tank step back toward it

        new StandardCombatSystem()
                .resolve(0, List.of(blue, red), CombatKit.clearTerrain(), FakeWeather.clear());

        assertEquals(3.0 - 0.3, blue.aim(), 1e-9, "aim must rotate toward, not away from, the target");
    }
}