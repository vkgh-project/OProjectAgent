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

/** Target selection: nearest reachable enemy, hysteresis, and waiting. */
class TargetSelectionTest {

    @Test
    void choosesTheNearestReachableEnemy() {
        NavGrid nav = new FakeNavGrid(100, 20.0);
        PathPlanner planner = FakePlanner.straight();
        Terrain terrain = MovementKit.flatTerrain(nav, planner);
        WeatherModel weather = FakeWeather.clear();

        UnitData blue = MovementKit.unit(0, Side.BLUE, UnitType.TANK, 1000.0, 1000.0);
        UnitData near = MovementKit.attacking(1, Side.RED, UnitType.TANK, 1600.0, 1000.0);
        UnitData far = MovementKit.attacking(2, Side.RED, UnitType.TANK, 1800.0, 1000.0);
        List<UnitData> units = List.of(blue, near, far);
        StandardMovementSystem system = new StandardMovementSystem();

        MovementKit.run(system, 500, units, terrain, weather);

        // chasing the near enemy (600 m) the tank stops at 500 m -> x = 1100;
        // chasing the far one (800 m) it would stop at x = 1300
        assertEquals(UnitState.READY, blue.state());
        assertEquals(1100.0, blue.x(), 2.0);
        assertTrue(MovementKit.distance(blue, near) <= 500.0 + 1e-9);
        assertTrue(MovementKit.distance(blue, far) > 500.0 - 1e-9);
    }

    @Test
    void keepsTargetUntilAnotherEnemyIsAtLeast40PercentCloser() {
        NavGrid nav = new FakeNavGrid(100, 20.0);
        PathPlanner planner = FakePlanner.straight();
        Terrain terrain = MovementKit.flatTerrain(nav, planner);
        WeatherModel weather = FakeWeather.clear();

        UnitData blue = MovementKit.unit(0, Side.BLUE, UnitType.TANK, 1000.0, 1000.0);
        UnitData target = MovementKit.attacking(1, Side.RED, UnitType.TANK, 1800.0, 1000.0);
        UnitData rival = MovementKit.unit(2, Side.RED, UnitType.TANK, 1900.0, 1000.0);
        List<UnitData> units = List.of(blue, target, rival);
        StandardMovementSystem system = new StandardMovementSystem();

        system.step(1, units, terrain, weather);
        // target is ~798.6 m away; the rival closes to 548.6 m, which is not
        // at least 40 percent closer (0.6 x 798.6 = 479.2) -> keep the target
        rival.setX(1550.0);
        system.step(2, units, terrain, weather);

        assertEquals(UnitState.MOVING, blue.state());
        assertTrue(blue.x() > 1001.0, "unit should still close on the kept target, x = " + blue.x());
    }

    @Test
    void switchesTargetOnceAnotherEnemyIsAtLeast40PercentCloser() {
        NavGrid nav = new FakeNavGrid(100, 20.0);
        PathPlanner planner = FakePlanner.straight();
        Terrain terrain = MovementKit.flatTerrain(nav, planner);
        WeatherModel weather = FakeWeather.clear();

        UnitData blue = MovementKit.unit(0, Side.BLUE, UnitType.TANK, 1000.0, 1000.0);
        UnitData target = MovementKit.attacking(1, Side.RED, UnitType.TANK, 1800.0, 1000.0);
        UnitData rival = MovementKit.unit(2, Side.RED, UnitType.TANK, 1900.0, 1000.0);
        List<UnitData> units = List.of(blue, target, rival);
        StandardMovementSystem system = new StandardMovementSystem();

        system.step(1, units, terrain, weather);
        // the rival closes to ~298.6 m, at least 40 percent closer than the
        // target -> switch; the rival is within weapon range -> hold position
        rival.setX(1300.0);
        system.step(2, units, terrain, weather);

        assertEquals(UnitState.READY, blue.state());
        assertTrue(MovementKit.distance(blue, rival) <= 500.0 + 1e-9);
    }

    @Test
    void unitWaitsWhenThereIsNoEnemy() {
        NavGrid nav = new FakeNavGrid(100, 20.0);
        PathPlanner planner = FakePlanner.straight();
        Terrain terrain = MovementKit.flatTerrain(nav, planner);
        WeatherModel weather = FakeWeather.clear();

        UnitData blue = MovementKit.unit(0, Side.BLUE, UnitType.TANK, 1000.0, 1000.0);
        StandardMovementSystem system = new StandardMovementSystem();

        system.step(1, List.of(blue), terrain, weather);

        assertEquals(UnitState.WAITING, blue.state());
        assertEquals(0.0, blue.odometer());
    }

    @Test
    void unitWaitsWhenTheEnemyIsOnAnotherNavComponent() {
        FakeNavGrid nav = new FakeNavGrid(100, 20.0);
        // blue at (1000, 1000) is in cell (50, 50); move the enemy's cell
        // (90, 50) to another component
        nav.setComponent(90, 50, 1);
        PathPlanner planner = FakePlanner.straight();
        Terrain terrain = MovementKit.flatTerrain(nav, planner);
        WeatherModel weather = FakeWeather.clear();

        UnitData blue = MovementKit.unit(0, Side.BLUE, UnitType.TANK, 1000.0, 1000.0);
        UnitData red = MovementKit.unit(1, Side.RED, UnitType.TANK, 1800.0, 1000.0);
        StandardMovementSystem system = new StandardMovementSystem();

        system.step(1, List.of(blue, red), terrain, weather);

        assertEquals(UnitState.WAITING, blue.state());
        assertEquals(0.0, blue.odometer());
    }

    @Test
    void unitWaitsWhenThePlannerHasNoPath() {
        NavGrid nav = new FakeNavGrid(100, 20.0);
        PathPlanner planner = FakePlanner.noPath();
        Terrain terrain = MovementKit.flatTerrain(nav, planner);
        WeatherModel weather = FakeWeather.clear();

        UnitData blue = MovementKit.unit(0, Side.BLUE, UnitType.TANK, 1000.0, 1000.0);
        UnitData red = MovementKit.unit(1, Side.RED, UnitType.TANK, 1800.0, 1000.0);
        StandardMovementSystem system = new StandardMovementSystem();

        system.step(1, List.of(blue, red), terrain, weather);

        assertEquals(UnitState.WAITING, blue.state());
        assertEquals(0.0, blue.odometer());
    }
}