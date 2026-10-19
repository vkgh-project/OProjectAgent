package ua.battlesim.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.NavGrid;
import ua.battlesim.api.PathPlanner;
import ua.battlesim.api.Side;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.WeatherModel;

/** Speed = baseSpeed x slope factor x road factor x weather factor x alignment. */
class SpeedFactorTest {

    @Test
    void vehicleOnRoadGetsThe135RoadFactor() {
        FakeNavGrid nav = new FakeNavGrid(100, 20.0);
        nav.setRoad(true);
        Terrain terrain = MovementKit.flatTerrain(nav, FakePlanner.straight());
        WeatherModel weather = FakeWeather.clear();

        UnitData blue = MovementKit.unit(0, Side.BLUE, UnitType.TANK, 1000.0, 500.0);
        UnitData red = MovementKit.attacking(1, Side.RED, UnitType.TANK, 1000.0, 1500.0);
        StandardMovementSystem system = new StandardMovementSystem();

        MovementKit.run(system, 10, List.of(blue, red), terrain, weather);

        assertEquals(10.0 * 14.0 * 1.35 * 0.1, blue.odometer(), 1e-9);
    }

    @Test
    void infantryOnRoadGetsThe110RoadFactor() {
        FakeNavGrid nav = new FakeNavGrid(100, 20.0);
        nav.setRoad(true);
        Terrain terrain = MovementKit.flatTerrain(nav, FakePlanner.straight());
        WeatherModel weather = FakeWeather.clear();

        UnitData blue = MovementKit.unit(0, Side.BLUE, UnitType.INFANTRY, 1000.0, 500.0);
        UnitData red = MovementKit.attacking(1, Side.RED, UnitType.TANK, 1000.0, 1500.0);
        StandardMovementSystem system = new StandardMovementSystem();

        MovementKit.run(system, 10, List.of(blue, red), terrain, weather);

        assertEquals(10.0 * 2.0 * 1.1 * 0.1, blue.odometer(), 1e-9);
    }

    @Test
    void climbingASlopeSlowsTheUnit() {
        NavGrid nav = new FakeNavGrid(100, 20.0);
        PathPlanner planner = FakePlanner.straight();
        // 10 percent ascending grade in the direction of travel -> factor 0.8
        Terrain terrain = new FakeTerrain(2000.0, 0.1, nav, planner);
        WeatherModel weather = FakeWeather.clear();

        UnitData blue = MovementKit.unit(0, Side.BLUE, UnitType.TANK, 1000.0, 500.0);
        UnitData red = MovementKit.attacking(1, Side.RED, UnitType.TANK, 1000.0, 1500.0);
        StandardMovementSystem system = new StandardMovementSystem();

        MovementKit.run(system, 10, List.of(blue, red), terrain, weather);

        assertEquals(10.0 * 14.0 * 0.8 * 0.1, blue.odometer(), 1e-9);
    }

    @Test
    void weatherSpeedFactorScalesTheMovement() {
        NavGrid nav = new FakeNavGrid(100, 20.0);
        PathPlanner planner = FakePlanner.straight();
        Terrain terrain = MovementKit.flatTerrain(nav, planner);
        WeatherModel weather = new FakeWeather(0.5, 1.0, 25_000.0);

        UnitData blue = MovementKit.unit(0, Side.BLUE, UnitType.TANK, 1000.0, 500.0);
        UnitData red = MovementKit.attacking(1, Side.RED, UnitType.TANK, 1000.0, 1500.0);
        StandardMovementSystem system = new StandardMovementSystem();

        MovementKit.run(system, 10, List.of(blue, red), terrain, weather);

        assertEquals(10.0 * 14.0 * 0.5 * 0.1, blue.odometer(), 1e-9);
    }
}