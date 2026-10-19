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

/** A unit deflects around a blocking unit instead of overlapping it. */
class AvoidsUnitOverlapTest {

    @Test
    void unitDeflectsAroundAllyInsteadOfOverlapping() {
        NavGrid nav = new FakeNavGrid(100, 20.0);
        PathPlanner planner = FakePlanner.straight();
        Terrain terrain = MovementKit.flatTerrain(nav, planner);
        WeatherModel weather = FakeWeather.clear();

        UnitData blue = MovementKit.unit(0, Side.BLUE, UnitType.TANK, 1000.0, 500.0);
        UnitData ally = MovementKit.attacking(1, Side.BLUE, UnitType.TANK, 1000.0, 600.0);
        UnitData red = MovementKit.attacking(2, Side.RED, UnitType.TANK, 1000.0, 1500.0);
        List<UnitData> units = List.of(blue, ally, red);
        StandardMovementSystem system = new StandardMovementSystem();

        double minDistance = Double.POSITIVE_INFINITY;
        int ticks = 0;
        while (ticks < 2000) {
            system.step(ticks + 1, units, terrain, weather);
            ticks++;
            minDistance = StrictMath.min(minDistance, MovementKit.distance(blue, ally));
            if (blue.state() == UnitState.READY) {
                break;
            }
        }

        assertEquals(UnitState.READY, blue.state(), "unit should eventually reach weapon range");
        assertTrue(MovementKit.distance(blue, red) <= 500.0 + 1e-9);
        // never overlapping the ally (tanks: 2 m + 2 m radius)
        assertTrue(minDistance >= 4.0 - 1e-9, "units overlapped: min distance " + minDistance);
        // the stationary ally is left untouched
        assertEquals(1000.0, ally.x());
        assertEquals(600.0, ally.z());
    }
}