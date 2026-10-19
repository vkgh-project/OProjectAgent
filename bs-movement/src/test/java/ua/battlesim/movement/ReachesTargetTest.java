package ua.battlesim.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.NavGrid;
import ua.battlesim.api.PathPlanner;
import ua.battlesim.api.Side;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.WeatherModel;

/** The unit chases its enemy, closes to weapon range, then holds position. */
class ReachesTargetTest {

    @Test
    void unitChasesEnemyAndStopsInWeaponRange() {
        NavGrid nav = new FakeNavGrid(100, 20.0);
        PathPlanner planner = FakePlanner.straight();
        Terrain terrain = MovementKit.flatTerrain(nav, planner);
        WeatherModel weather = FakeWeather.clear();

        UnitData blue = MovementKit.unit(0, Side.BLUE, UnitType.TANK, 1000.0, 500.0);
        UnitData red = MovementKit.attacking(1, Side.RED, UnitType.TANK, 1000.0, 1500.0);
        List<UnitData> units = List.of(blue, red);
        StandardMovementSystem system = new StandardMovementSystem();

        MovementKit.run(system, 500, units, terrain, weather);

        assertEquals(UnitState.READY, blue.state());
        double distance = MovementKit.distance(blue, red);
        assertTrue(distance <= 500.0 + 1e-9, "expected to stop within weapon range, was " + distance);
        // 358 ticks of 1.4 m (14 m/s x 0.1 s) close the 1000 m gap to 498.8 m
        assertEquals(358.0 * 14.0 * 0.1, blue.odometer(), 1e-6);
        // the stationary engaging enemy is left untouched
        assertEquals(1000.0, red.x());
        assertEquals(1500.0, red.z());
    }
}