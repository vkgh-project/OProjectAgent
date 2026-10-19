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
import ua.battlesim.api.Vec2;
import ua.battlesim.api.WeatherModel;

/**
 * A unit stuck in a dead end (no walkable progress for 80 ticks) replans and
 * recovers once the route opens, reaching its target afterwards.
 */
class RecoversFromDeadEndTest {

    @Test
    void stuckUnitReplansAndRecoversWhenPathOpens() {
        FakeNavGrid nav = new FakeNavGrid(100, 20.0);
        Vec2 deadEnd = new Vec2(1000.0, 860.0);
        nav.setPocket(deadEnd, 80.0);
        // the planner routes through the dead end until the stuck replan
        // (its third plan call) opens the pocket and switches to a direct path
        FakePlanner planner = FakePlanner.via(deadEnd);
        planner.switchAfter(2, nav::clearPocket);
        Terrain terrain = MovementKit.flatTerrain(nav, planner);
        WeatherModel weather = FakeWeather.clear();

        UnitData blue = MovementKit.unit(0, Side.BLUE, UnitType.TANK, 1000.0, 800.0);
        UnitData red = MovementKit.attacking(1, Side.RED, UnitType.TANK, 1000.0, 1800.0);
        List<UnitData> units = List.of(blue, red);
        StandardMovementSystem system = new StandardMovementSystem();

        MovementKit.run(system, 40, units, terrain, weather);
        // completely blocked inside the dead-end pocket: no progress at all
        assertEquals(0.0, blue.odometer());
        assertEquals(1000.0, blue.x());
        assertEquals(800.0, blue.z());

        int ticks = 40;
        while (blue.state() != UnitState.READY && ticks < 2000) {
            system.step(ticks + 1, units, terrain, weather);
            ticks++;
        }

        assertEquals(UnitState.READY, blue.state(), "unit should recover and reach weapon range");
        assertTrue(MovementKit.distance(blue, red) <= 500.0 + 1e-9);
        assertTrue(blue.odometer() > 0.0, "unit should have moved after the dead end opened");
    }
}