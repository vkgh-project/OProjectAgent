package ua.battlesim.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ua.battlesim.api.Attack;
import ua.battlesim.api.Side;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitType;

/**
 * All nine attacker/target damage pairs of the shared {@link UnitInfo}
 * table, resolved through one real combat tick.
 */
class DamageTableTest {

    @ParameterizedTest
    @CsvSource({
            "TANK, TANK, 20",
            "TANK, INFANTRY, 50",
            "TANK, APC_INFANTRY, 25",
            "INFANTRY, TANK, 10",
            "INFANTRY, INFANTRY, 50",
            "INFANTRY, APC_INFANTRY, 20",
            "APC_INFANTRY, TANK, 10",
            "APC_INFANTRY, INFANTRY, 50",
            "APC_INFANTRY, APC_INFANTRY, 20",
    })
    void allNineDamagePairs(UnitType attackerType, UnitType targetType, int expectedDamage) {
        UnitData attacker = CombatKit.unit(1, Side.BLUE, attackerType, 0.0, 0.0);
        UnitData target = CombatKit.unit(2, Side.RED, targetType, 100.0, 0.0);
        List<Attack> attacks = new StandardCombatSystem()
                .resolve(0, List.of(attacker, target), CombatKit.clearTerrain(), FakeWeather.clear());

        Attack shot = attacks.stream()
                .filter(a -> a.attackerId() == 1 && a.targetId() == 2)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no shot 1 -> 2 in " + attacks));
        assertEquals(expectedDamage, shot.damage(),
                attackerType + " vs " + targetType + " must deal the table damage");
        assertFalse(shot.killed(), "a 100-health target must survive one hit of " + shot.damage());
        assertEquals(100 - expectedDamage, target.health());
    }
}