package ua.battlesim.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests the round setup record with fakes of the neighbouring modules
 * (bs-terrain, bs-weather).
 */
class RoundSetupTest {

    private static UnitData unit() {
        return new UnitData(0, Side.BLUE, UnitType.TANK, 100.0, 100.0, 0.0, 0.0, 100, UnitState.READY, 0, 0.0, -1);
    }

    @Test
    void acceptsACompleteSetupAndCopiesTheUnitList() {
        UnitData a = unit();
        UnitData b = new UnitData(1, Side.RED, UnitType.INFANTRY, 1900.0, 1900.0, 3.14, 3.14, 100, UnitState.READY, 0, 0.0, -1);
        Terrain t = TestFakes.terrain();
        WeatherModel w = TestFakes.weather();
        RoundSetup setup = new RoundSetup(1, 42L, t, List.of(a, b), w);
        assertEquals(1, setup.number());
        assertEquals(42L, setup.seed());
        assertSame(t, setup.terrain());
        assertSame(w, setup.weather());
        assertEquals(2, setup.units().size());
        // the record holds a defensive copy
        assertThrows(UnsupportedOperationException.class, () -> setup.units().add(a));
    }

    @Test
    void rejectsIncompleteSetups() {
        UnitData a = unit();
        Terrain t = TestFakes.terrain();
        WeatherModel w = TestFakes.weather();
        assertThrows(IllegalArgumentException.class, () -> new RoundSetup(0, 1L, t, List.of(a), w));
        assertThrows(NullPointerException.class, () -> new RoundSetup(1, 1L, null, List.of(a), w));
        assertThrows(NullPointerException.class, () -> new RoundSetup(1, 1L, t, null, w));
        assertThrows(IllegalArgumentException.class, () -> new RoundSetup(1, 1L, t, List.of(), w));
        assertThrows(NullPointerException.class, () -> new RoundSetup(1, 1L, t, List.of(a), null));
    }
}
