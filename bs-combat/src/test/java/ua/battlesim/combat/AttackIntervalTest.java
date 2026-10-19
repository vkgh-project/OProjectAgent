package ua.battlesim.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.Attack;
import ua.battlesim.api.Side;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitType;

/**
 * Attack cadence: a shot sets the cooldown to the interval, and the unit
 * shoots again exactly interval ticks later.
 */
class AttackIntervalTest {

    @Test
    void intervalIsExactly20Ticks() {
        StandardCombatSystem combat = new StandardCombatSystem();
        assertEquals(20, combat.attackIntervalTicks(), "default interval is 20 ticks");

        // Two tanks 250 m apart (20 damage each, both in range): only unit 1
        // is observed; the victim survives 3 hits of 20 (100 -> 80 -> 60 -> 40).
        UnitData blue = CombatKit.unit(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        UnitData red = CombatKit.unit(2, Side.RED, UnitType.TANK, 250.0, 0.0);

        List<Integer> firedAt = new ArrayList<>();
        for (int tick = 0; tick <= 49; tick++) {
            List<Attack> attacks = combat.resolve(tick, List.of(blue, red),
                    CombatKit.clearTerrain(), FakeWeather.clear());
            if (attacks.stream().anyMatch(a -> a.attackerId() == 1)) {
                firedAt.add(tick);
            }
        }
        assertEquals(List.of(0, 20, 40), firedAt, "consecutive shots exactly 20 ticks apart");
        assertEquals(40, red.health(), "three hits of 20: 100 -> 80 -> 60 -> 40");
    }

    @Test
    void cooldownCountsDownBeforeTheNextShot() {
        StandardCombatSystem combat = new StandardCombatSystem();
        UnitData blue = CombatKit.unit(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        UnitData red = CombatKit.unit(2, Side.RED, UnitType.INFANTRY, 250.0, 0.0);
        blue.setCooldown(3);

        List<Integer> firedAt = new ArrayList<>();
        for (int tick = 0; tick <= 22; tick++) {
            List<Attack> attacks = combat.resolve(tick, List.of(blue, red),
                    CombatKit.clearTerrain(), FakeWeather.clear());
            if (attacks.stream().anyMatch(a -> a.attackerId() == 1)) {
                firedAt.add(tick);
            }
        }
        assertEquals(List.of(2, 22), firedAt,
                "3-tick cooldown fires on tick 2, then exactly 20 ticks later");
    }

    @Test
    void afterAShotTheCooldownIsTheFullInterval() {
        StandardCombatSystem combat = new StandardCombatSystem();
        UnitData blue = CombatKit.unit(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        UnitData red = CombatKit.unit(2, Side.RED, UnitType.INFANTRY, 250.0, 0.0);

        combat.resolve(0, List.of(blue, red), CombatKit.clearTerrain(), FakeWeather.clear());
        assertEquals(20, blue.cooldown(), "a fresh shooter reloads to the full interval");
        combat.resolve(1, List.of(blue, red), CombatKit.clearTerrain(), FakeWeather.clear());
        assertEquals(19, blue.cooldown(), "the cooldown decrements one tick at a time");
    }
}