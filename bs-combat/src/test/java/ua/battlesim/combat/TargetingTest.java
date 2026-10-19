package ua.battlesim.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.Attack;
import ua.battlesim.api.Side;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;

/** Target choice: nearest visible enemy in range, ties to the lowest id. */
class TargetingTest {

    @Test
    void noShotWithoutLineOfSight() {
        UnitData blue = CombatKit.unit(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        UnitData red = CombatKit.unit(2, Side.RED, UnitType.INFANTRY, 100.0, 0.0);

        List<Attack> attacks = new StandardCombatSystem()
                .resolve(0, List.of(blue, red), CombatKit.blockedTerrain(), FakeWeather.clear());

        assertEquals(List.of(), attacks, "blocked line of sight: nobody may fire");
        assertNotEquals(UnitState.ATTACKING, blue.state(), "no target: no ATTACKING state");
        assertEquals(100, red.health());
    }

    @Test
    void nearestEnemyWins() {
        UnitData blue = CombatKit.unit(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        UnitData near = CombatKit.unit(5, Side.RED, UnitType.INFANTRY, 50.0, 0.0);
        UnitData far = CombatKit.unit(3, Side.RED, UnitType.INFANTRY, 50.0, 50.0);

        List<Attack> attacks = new StandardCombatSystem()
                .resolve(0, List.of(blue, far, near), CombatKit.clearTerrain(), FakeWeather.clear());

        Attack blueShot = attacks.stream()
                .filter(a -> a.attackerId() == 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("blue must shoot: " + attacks));
        assertEquals(5, blueShot.targetId(), "the nearer enemy must be targeted, not the lower id");
    }

    @Test
    void distanceTieGoesToTheLowestId() {
        UnitData blue = CombatKit.unit(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        UnitData east = CombatKit.unit(5, Side.RED, UnitType.INFANTRY, 50.0, 0.0);
        UnitData south = CombatKit.unit(3, Side.RED, UnitType.INFANTRY, 0.0, 50.0);

        List<Attack> attacks = new StandardCombatSystem()
                .resolve(0, List.of(blue, east, south), CombatKit.clearTerrain(), FakeWeather.clear());

        Attack blueShot = attacks.stream()
                .filter(a -> a.attackerId() == 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("blue must shoot: " + attacks));
        assertEquals(3, blueShot.targetId(), "exact distance tie: the lowest id wins");
    }

    @Test
    void destroyedEnemiesAreNotTargeted() {
        UnitData blue = CombatKit.unit(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        UnitData dead = CombatKit.destroyed(2, Side.RED, UnitType.INFANTRY, 100.0, 0.0);
        dead.setCooldown(5);

        List<Attack> attacks = new StandardCombatSystem()
                .resolve(0, List.of(blue, dead), CombatKit.clearTerrain(), FakeWeather.clear());

        assertEquals(List.of(), attacks, "a destroyed enemy is not a valid target");
        assertEquals(5, dead.cooldown(), "a destroyed unit keeps its cooldown");
    }

    @Test
    void destroyedUnitsDoNotAct() {
        UnitData blue = CombatKit.destroyed(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        blue.setCooldown(5);
        UnitData red = CombatKit.unit(2, Side.RED, UnitType.INFANTRY, 50.0, 0.0);

        List<Attack> attacks = new StandardCombatSystem()
                .resolve(0, List.of(blue, red), CombatKit.clearTerrain(), FakeWeather.clear());

        assertEquals(List.of(), attacks);
        assertEquals(5, blue.cooldown(), "a destroyed unit must not decrement its cooldown");
    }
}