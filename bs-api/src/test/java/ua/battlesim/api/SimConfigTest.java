package ua.battlesim.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests the default configuration and the constructor validation.
 */
class SimConfigTest {

    @Test
    void defaultsMatchTheArchitectureDocument() {
        SimConfig c = SimConfig.defaults();
        assertEquals(2000.0, c.mapSize());
        assertEquals(0.1, c.stepSeconds());
        assertEquals(20, c.attackIntervalTicks());
        assertEquals(18000, c.maxRoundTicks());
        assertEquals(20, c.readyTicks());
        assertEquals(1, c.minUnits());
        assertEquals(20, c.maxUnits());
        assertFalse(c.weatherEnabled());
    }

    @Test
    void validCustomConfigurationIsAccepted() {
        SimConfig c = new SimConfig(1000.0, 0.2, 10, 9000, 5, 2, 10, true);
        assertEquals(1000.0, c.mapSize());
        assertEquals(0.2, c.stepSeconds());
        assertEquals(10, c.attackIntervalTicks());
        assertEquals(9000, c.maxRoundTicks());
        assertEquals(5, c.readyTicks());
        assertEquals(2, c.minUnits());
        assertEquals(10, c.maxUnits());
        assertEquals(true, c.weatherEnabled());
    }

    @Test
    void invalidArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SimConfig(-1.0, 0.1, 20, 18000, 20, 1, 20, false));
        assertThrows(IllegalArgumentException.class, () -> new SimConfig(Double.NaN, 0.1, 20, 18000, 20, 1, 20, false));
        assertThrows(IllegalArgumentException.class, () -> new SimConfig(2000.0, 0.0, 20, 18000, 20, 1, 20, false));
        assertThrows(IllegalArgumentException.class, () -> new SimConfig(2000.0, 0.1, 0, 18000, 20, 1, 20, false));
        assertThrows(IllegalArgumentException.class, () -> new SimConfig(2000.0, 0.1, 20, 0, 20, 1, 20, false));
        assertThrows(IllegalArgumentException.class, () -> new SimConfig(2000.0, 0.1, 20, 18000, -1, 1, 20, false));
        assertThrows(IllegalArgumentException.class, () -> new SimConfig(2000.0, 0.1, 20, 18000, 20, 0, 20, false));
        assertThrows(IllegalArgumentException.class, () -> new SimConfig(2000.0, 0.1, 20, 18000, 20, 5, 4, false));
    }
}
