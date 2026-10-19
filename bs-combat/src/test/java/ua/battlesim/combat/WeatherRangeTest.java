package ua.battlesim.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.Attack;
import ua.battlesim.api.Side;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitType;

/**
 * The effective range is {@code min(tableRange x combatFactor, visibility)}:
 * bad visibility and low combat factor both shrink the reach.
 */
class WeatherRangeTest {

    @Test
    void rangeShrinksInBadVisibility() {
        // 150 m apart: a tank (500 m table range) reaches its target when it
        // is clear but not when the visibility is only 100 m.
        List<Attack> clear = resolve(150.0, 1.0, 25_000.0);
        assertEquals(2, clear.size(), "clear weather: both units are in range: " + clear);

        List<Attack> fog = resolve(150.0, 1.0, 100.0);
        assertEquals(List.of(), fog,
                "150 m > 100 m visibility: nobody may fire in the fog");
    }

    @Test
    void combatFactorShrinksTheRange() {
        // 160 m apart: within the clear tank range (500 m), outside it with a
        // 0.3 combat factor (500 x 0.3 = 150 m).
        List<Attack> clear = resolve(160.0, 1.0, 25_000.0);
        assertEquals(2, clear.size(), "clear weather: both units are in range: " + clear);

        List<Attack> rain = resolve(160.0, 0.3, 25_000.0);
        assertEquals(List.of(), rain, "160 m > 150 m effective range: no shots in the rain");

        List<Attack> drizzle = resolve(160.0, 0.5, 25_000.0);
        assertEquals(1, drizzle.size(), "160 m < 250 m effective tank range; the 100 m "
                + "infantry reach does not cover it: " + drizzle);
        assertEquals(1, drizzle.get(0).attackerId(), "only the tank may shoot");
    }

    private static List<Attack> resolve(double distance, double combatFactor, double visibility) {
        UnitData blue = CombatKit.unit(1, Side.BLUE, UnitType.TANK, 0.0, 0.0);
        UnitData red = CombatKit.unit(2, Side.RED, UnitType.INFANTRY, distance, 0.0);
        return new StandardCombatSystem()
                .resolve(0, List.of(blue, red), CombatKit.clearTerrain(),
                        new FakeWeather(combatFactor, visibility));
    }
}