package ua.battlesim.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests the mutable unit state: construction, mutation and validation.
 */
class UnitDataTest {

    private static UnitData fresh() {
        return new UnitData(3, Side.BLUE, UnitType.TANK, 100.0, 200.0, 0.5, 0.25, 100, UnitState.READY, 0, 0.0, -1);
    }

    @Test
    void constructorStoresAllFields() {
        UnitData u = fresh();
        assertEquals(3, u.id());
        assertEquals(Side.BLUE, u.side());
        assertEquals(UnitType.TANK, u.type());
        assertEquals(100.0, u.x());
        assertEquals(200.0, u.z());
        assertEquals(0.5, u.heading());
        assertEquals(0.25, u.aim());
        assertEquals(100, u.health());
        assertEquals(UnitState.READY, u.state());
        assertEquals(0, u.cooldown());
        assertEquals(0.0, u.odometer());
        assertEquals(-1, u.destroyedTick());
    }

    @Test
    void settersMutateState() {
        UnitData u = fresh();
        u.setSide(Side.RED);
        u.setType(UnitType.INFANTRY);
        u.setX(150.0);
        u.setZ(250.0);
        u.setHeading(1.0);
        u.setAim(0.5);
        u.setHealth(80);
        u.setState(UnitState.MOVING);
        u.setCooldown(5);
        u.setOdometer(50.0);
        u.setDestroyedTick(42);
        assertEquals(Side.RED, u.side());
        assertEquals(UnitType.INFANTRY, u.type());
        assertEquals(150.0, u.x());
        assertEquals(250.0, u.z());
        assertEquals(1.0, u.heading());
        assertEquals(0.5, u.aim());
        assertEquals(80, u.health());
        assertEquals(UnitState.MOVING, u.state());
        assertEquals(5, u.cooldown());
        assertEquals(50.0, u.odometer());
        assertEquals(42, u.destroyedTick());
    }

    @Test
    void constructorValidatesArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new UnitData(-1, Side.BLUE, UnitType.TANK, 0.0, 0.0, 0.0, 0.0, 100, UnitState.READY, 0, 0.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitData(1, null, UnitType.TANK, 0.0, 0.0, 0.0, 0.0, 100, UnitState.READY, 0, 0.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitData(1, Side.BLUE, null, 0.0, 0.0, 0.0, 0.0, 100, UnitState.READY, 0, 0.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitData(1, Side.BLUE, UnitType.TANK, Double.NaN, 0.0, 0.0, 0.0, 100, UnitState.READY, 0, 0.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitData(1, Side.BLUE, UnitType.TANK, 0.0, Double.POSITIVE_INFINITY, 0.0, 0.0, 100, UnitState.READY, 0, 0.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitData(1, Side.BLUE, UnitType.TANK, 0.0, 0.0, Double.NaN, 0.0, 100, UnitState.READY, 0, 0.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitData(1, Side.BLUE, UnitType.TANK, 0.0, 0.0, 0.0, Double.NaN, 100, UnitState.READY, 0, 0.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitData(1, Side.BLUE, UnitType.TANK, 0.0, 0.0, 0.0, 0.0, -1, UnitState.READY, 0, 0.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitData(1, Side.BLUE, UnitType.TANK, 0.0, 0.0, 0.0, 0.0, 101, UnitState.READY, 0, 0.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitData(1, Side.BLUE, UnitType.TANK, 0.0, 0.0, 0.0, 0.0, 100, null, 0, 0.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitData(1, Side.BLUE, UnitType.TANK, 0.0, 0.0, 0.0, 0.0, 100, UnitState.READY, -1, 0.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitData(1, Side.BLUE, UnitType.TANK, 0.0, 0.0, 0.0, 0.0, 100, UnitState.READY, 0, -1.0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new UnitData(1, Side.BLUE, UnitType.TANK, 0.0, 0.0, 0.0, 0.0, 100, UnitState.READY, 0, 0.0, -2));
    }

    @Test
    void settersValidateArgumentsAndLeaveTheValueOnFailure() {
        UnitData u = fresh();
        assertThrows(IllegalArgumentException.class, () -> u.setSide(null));
        assertThrows(IllegalArgumentException.class, () -> u.setType(null));
        assertThrows(IllegalArgumentException.class, () -> u.setX(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> u.setZ(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> u.setHeading(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> u.setAim(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> u.setHealth(-1));
        assertThrows(IllegalArgumentException.class, () -> u.setHealth(101));
        assertThrows(IllegalArgumentException.class, () -> u.setState(null));
        assertThrows(IllegalArgumentException.class, () -> u.setCooldown(-1));
        assertThrows(IllegalArgumentException.class, () -> u.setOdometer(-0.5));
        assertThrows(IllegalArgumentException.class, () -> u.setDestroyedTick(-2));
        // rejected values must not have been applied
        assertEquals(Side.BLUE, u.side());
        assertEquals(UnitType.TANK, u.type());
        assertEquals(100.0, u.x());
        assertEquals(200.0, u.z());
        assertEquals(0.5, u.heading());
        assertEquals(0.25, u.aim());
        assertEquals(100, u.health());
        assertEquals(UnitState.READY, u.state());
        assertEquals(0, u.cooldown());
        assertEquals(0.0, u.odometer());
        assertEquals(-1, u.destroyedTick());
    }
}
