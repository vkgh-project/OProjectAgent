package ua.battlesim.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the standard per-unit-type parameters, the range lookup and the
 * damage table (attacker -&gt; target).
 */
class UnitInfoTest {

    @Test
    void ofReturnsStandardParametersPerType() {
        UnitInfo tank = UnitInfo.of(UnitType.TANK);
        assertEquals(UnitType.TANK, tank.type());
        assertTrue(tank.radius() > 0.0);
        assertTrue(tank.speed() > 0.0);
        assertTrue(tank.turnRate() > 0.0);
        assertTrue(tank.eyeHeight() > 0.0);
        assertEquals(500.0, tank.range());

        assertEquals(UnitType.INFANTRY, UnitInfo.of(UnitType.INFANTRY).type());
        assertEquals(200.0, UnitInfo.of(UnitType.INFANTRY).range());

        assertEquals(UnitType.APC_INFANTRY, UnitInfo.of(UnitType.APC_INFANTRY).type());
        assertEquals(300.0, UnitInfo.of(UnitType.APC_INFANTRY).range());
    }

    @Test
    void rangeLookupMatchesTheConstants() {
        assertEquals(UnitInfo.TANK.range(), UnitInfo.range(UnitType.TANK));
        assertEquals(UnitInfo.INFANTRY.range(), UnitInfo.range(UnitType.INFANTRY));
        assertEquals(UnitInfo.APC_INFANTRY.range(), UnitInfo.range(UnitType.APC_INFANTRY));
    }

    @Test
    void damageTableMatchesTheArchitectureDocument() {
        // tank: 20 / 50 / 25 at 500 m
        assertEquals(20, UnitInfo.damage(UnitType.TANK, UnitType.TANK));
        assertEquals(50, UnitInfo.damage(UnitType.TANK, UnitType.INFANTRY));
        assertEquals(25, UnitInfo.damage(UnitType.TANK, UnitType.APC_INFANTRY));
        // infantry: 10 / 50 / 20 at 200 m
        assertEquals(10, UnitInfo.damage(UnitType.INFANTRY, UnitType.TANK));
        assertEquals(50, UnitInfo.damage(UnitType.INFANTRY, UnitType.INFANTRY));
        assertEquals(20, UnitInfo.damage(UnitType.INFANTRY, UnitType.APC_INFANTRY));
        // apc infantry: 10 / 50 / 20 at 300 m
        assertEquals(10, UnitInfo.damage(UnitType.APC_INFANTRY, UnitType.TANK));
        assertEquals(50, UnitInfo.damage(UnitType.APC_INFANTRY, UnitType.INFANTRY));
        assertEquals(20, UnitInfo.damage(UnitType.APC_INFANTRY, UnitType.APC_INFANTRY));
    }

    @Test
    void lookupsRejectNulls() {
        assertThrows(IllegalArgumentException.class, () -> UnitInfo.of(null));
        assertThrows(IllegalArgumentException.class, () -> UnitInfo.range(null));
        assertThrows(IllegalArgumentException.class, () -> UnitInfo.damage(null, UnitType.TANK));
        assertThrows(IllegalArgumentException.class, () -> UnitInfo.damage(UnitType.TANK, null));
    }

    @Test
    void constructorValidatesParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> new UnitInfo(null, 2.0, 14.0, 0.5, 2.5, 500.0, 20, 50, 25));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitInfo(UnitType.TANK, 0.0, 14.0, 0.5, 2.5, 500.0, 20, 50, 25));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitInfo(UnitType.TANK, 2.0, -1.0, 0.5, 2.5, 500.0, 20, 50, 25));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitInfo(UnitType.TANK, 2.0, 14.0, 0.5, 0.0, 500.0, 20, 50, 25));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitInfo(UnitType.TANK, 2.0, 14.0, 0.5, 2.5, 0.0, 20, 50, 25));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitInfo(UnitType.TANK, 2.0, 14.0, 0.5, 2.5, 500.0, -1, 50, 25));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitInfo(UnitType.TANK, 2.0, 14.0, 0.5, 2.5, 500.0, 20, 101, 25));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitInfo(UnitType.TANK, 2.0, 14.0, 0.5, 2.5, 500.0, 20, 50, -5));
    }
}
