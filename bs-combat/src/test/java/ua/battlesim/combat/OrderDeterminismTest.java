package ua.battlesim.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.Attack;
import ua.battlesim.api.Side;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;

/**
 * Determinism: the result is independent of the order the engine passes the
 * units in; units are processed in ascending id order.
 */
class OrderDeterminismTest {

    @Test
    void resultDoesNotDependOnTheUnitListOrder() {
        UnitData tankOne = CombatKit.unit(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        UnitData tankTwo = CombatKit.unit(2, Side.BLUE, UnitType.TANK, 0.0, 50.0);
        UnitData enemy = CombatKit.unit(3, Side.RED, UnitType.INFANTRY, 100.0, 25.0);

        List<Attack> attacks = new StandardCombatSystem()
                .resolve(0, List.of(tankTwo, tankOne, enemy), CombatKit.clearTerrain(),
                        FakeWeather.clear());

        assertEquals(3, attacks.size(), "both tanks plus the enemy's return shot: " + attacks);
        assertEquals(List.of(1, 2, 3), attacks.stream().map(Attack::attackerId).toList(),
                "attacks are reported in ascending attacker id order");
        assertEquals(3, attacks.get(0).targetId());
        assertEquals(3, attacks.get(1).targetId());
        assertEquals(50, attacks.get(0).damage());
        assertEquals(50, attacks.get(1).damage());
        // both tank hits (50 + 50) fall in the same tick: the infantry dies
        assertEquals(true, attacks.get(0).killed());
        assertEquals(true, attacks.get(1).killed());
        assertEquals(0, enemy.health());
        assertEquals(UnitState.DESTROYED, enemy.state());
        // the enemy sees both tanks at exactly the same distance: lowest id
        assertEquals(1, attacks.get(2).targetId());
        assertEquals(10, attacks.get(2).damage());
        assertEquals(90, tankOne.health());
    }

    @Test
    void sameInputsGiveTheSameResult() {
        StandardCombatSystem combat = new StandardCombatSystem();

        UnitData blueOne = CombatKit.unit(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        UnitData redOne = CombatKit.unit(2, Side.RED, UnitType.INFANTRY, 150.0, 0.0);
        List<Attack> first = combat.resolve(0, List.of(blueOne, redOne),
                CombatKit.clearTerrain(), FakeWeather.clear());

        UnitData blueTwo = CombatKit.unit(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        UnitData redTwo = CombatKit.unit(2, Side.RED, UnitType.INFANTRY, 150.0, 0.0);
        List<Attack> second = combat.resolve(0, List.of(redTwo, blueTwo),
                CombatKit.clearTerrain(), FakeWeather.clear());

        assertEquals(first, second, "same state: identical attacks regardless of list order");
        assertEquals(first, newFreshState(),
                "repeatable for fresh identical state");
    }

    private static List<Attack> newFreshState() {
        UnitData blue = CombatKit.unit(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        UnitData red = CombatKit.unit(2, Side.RED, UnitType.INFANTRY, 150.0, 0.0);
        return new StandardCombatSystem().resolve(1, List.of(blue, red),
                CombatKit.clearTerrain(), FakeWeather.clear());
    }
}