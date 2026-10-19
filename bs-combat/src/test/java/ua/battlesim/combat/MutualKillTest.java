package ua.battlesim.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.Attack;
import ua.battlesim.api.Side;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;

/** A mutual kill produces two destroyed units (simultaneous application). */
class MutualKillTest {

    @Test
    void mutualKillGivesTwoDestroyedUnits() {
        UnitData blue = CombatKit.unit(1, Side.BLUE, UnitType.INFANTRY, 0.0, 0.0);
        UnitData red = CombatKit.unit(2, Side.RED, UnitType.INFANTRY, 100.0, 0.0);
        blue.setHealth(50);
        red.setHealth(50);

        List<Attack> attacks = new StandardCombatSystem()
                .resolve(7, List.of(blue, red), CombatKit.clearTerrain(), FakeWeather.clear());

        assertEquals(2, attacks.size(), "both units must shoot in the same tick: " + attacks);
        for (Attack attack : attacks) {
            assertEquals(50, attack.damage());
            assertEquals(true, attack.killed(),
                    "mutual kill: every shot of this tick kills: " + attack);
        }
        assertEquals(UnitState.DESTROYED, blue.state());
        assertEquals(0, blue.health());
        assertEquals(7, blue.destroyedTick());
        assertEquals(UnitState.DESTROYED, red.state());
        assertEquals(0, red.health());
        assertEquals(7, red.destroyedTick());
    }
}