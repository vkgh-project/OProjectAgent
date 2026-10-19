package ua.battlesim.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests the constructor validation of the world and rule DTOs.
 */
class WorldValidationTest {

    @Test
    void vec2RejectsNonFiniteCoordinates() {
        assertDoesNotThrow(() -> new Vec2(-12.5, 3.25));
        assertThrows(IllegalArgumentException.class, () -> new Vec2(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Vec2(0.0, Double.POSITIVE_INFINITY));
    }

    @Test
    void propRejectsInvalidValues() {
        assertDoesNotThrow(() -> new Prop(1.0, 2.0, 1.5, 0.7, 0.3, 4));
        assertThrows(IllegalArgumentException.class, () -> new Prop(Double.NaN, 0.0, 1.0, 0.0, 0.0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Prop(0.0, 0.0, 0.0, 0.0, 0.0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Prop(0.0, 0.0, 1.0, Double.NaN, 0.0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Prop(0.0, 0.0, 1.0, 0.0, 1.5, 0));
        assertThrows(IllegalArgumentException.class, () -> new Prop(0.0, 0.0, 1.0, 0.0, 0.0, -1));
    }

    @Test
    void roadValidatesPointsWidthKindAndBridgeFlags() {
        assertDoesNotThrow(() -> new Road(
                List.of(new Vec2(0.0, 0.0), new Vec2(10.0, 0.0)), 6.0, 0, new boolean[] { false, true }));
        assertThrows(IllegalArgumentException.class, () -> new Road(List.of(), 6.0, 0, new boolean[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new Road(List.of(new Vec2(0.0, 0.0)), 0.0, 0, new boolean[] { false }));
        assertThrows(IllegalArgumentException.class,
                () -> new Road(List.of(new Vec2(0.0, 0.0)), 6.0, -1, new boolean[] { false }));
        assertThrows(IllegalArgumentException.class,
                () -> new Road(List.of(new Vec2(0.0, 0.0)), 6.0, 0, new boolean[] { false, true }));
        assertThrows(NullPointerException.class, () -> new Road(List.of(new Vec2(0.0, 0.0)), 6.0, 0, null));
    }

    @Test
    void bridgeValidatesEndpointsAndDimensions() {
        assertDoesNotThrow(() -> new Bridge(new Vec2(0.0, 0.0), new Vec2(5.0, 0.0), 1.2, 4.0));
        assertThrows(NullPointerException.class, () -> new Bridge(null, new Vec2(5.0, 0.0), 1.2, 4.0));
        assertThrows(NullPointerException.class, () -> new Bridge(new Vec2(0.0, 0.0), null, 1.2, 4.0));
        assertThrows(IllegalArgumentException.class, () -> new Bridge(new Vec2(0.0, 0.0), new Vec2(5.0, 0.0), -0.1, 4.0));
        assertThrows(IllegalArgumentException.class, () -> new Bridge(new Vec2(0.0, 0.0), new Vec2(5.0, 0.0), 1.2, 0.0));
    }

    @Test
    void weatherStateValidatesIntensitiesAndWind() {
        assertDoesNotThrow(() -> new WeatherState(0.2, 0.1, 0.0, 5.0, 1.5, 0.6, 0.3, 0.2, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new WeatherState(1.1, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new WeatherState(-0.1, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new WeatherState(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new WeatherState(0.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new WeatherState(0.0, 0.0, 0.0, 0.0, Double.NaN, 0.0, 0.0, 0.0, 0.0));
    }

    @Test
    void attackValidatesIdsAndDamage() {
        assertDoesNotThrow(() -> new Attack(1, 2, 20, false));
        assertThrows(IllegalArgumentException.class, () -> new Attack(-1, 2, 20, false));
        assertThrows(IllegalArgumentException.class, () -> new Attack(1, -1, 20, false));
        assertThrows(IllegalArgumentException.class, () -> new Attack(1, 1, 20, false));
        assertThrows(IllegalArgumentException.class, () -> new Attack(1, 2, -1, false));
    }
}
