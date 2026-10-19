package ua.battlesim.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.NavGrid;
import ua.battlesim.api.PathPlanner;
import ua.battlesim.api.Side;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.WeatherModel;

/** The simulation is deterministic and independent of the input unit order. */
class DeterminismTest {

    private static final int TICKS = 300;

    @Test
    void twoFreshRunsAreBitIdentical() {
        assertEquals(runScenario(scenario(), TICKS), runScenario(scenario(), TICKS));
    }

    @Test
    void inputOrderDoesNotAffectTheResult() {
        List<UnitData> forward = scenario();
        List<UnitData> reversed = new ArrayList<>(scenario());
        Collections.reverse(reversed);
        assertEquals(runScenario(forward, TICKS), runScenario(reversed, TICKS));
    }

    /**
     * Mixed scene: two moving tanks chase a common target with a stationary
     * obstacle on one of their direct routes, a moving infantry squad, and
     * two more stationary enemies; exercises target selection, deflection
     * and turn-rate code paths.
     */
    private static List<UnitData> scenario() {
        List<UnitData> units = new ArrayList<>();
        units.add(MovementKit.unit(0, Side.BLUE, UnitType.TANK, 900.0, 500.0));
        units.add(MovementKit.unit(1, Side.BLUE, UnitType.TANK, 1100.0, 700.0));
        units.add(MovementKit.attacking(3, Side.BLUE, UnitType.TANK, 1020.0, 780.0));
        units.add(MovementKit.unit(4, Side.BLUE, UnitType.INFANTRY, 800.6, 600.4));
        units.add(MovementKit.unit(10, Side.RED, UnitType.TANK, 1000.0, 1600.0));
        units.add(MovementKit.attacking(11, Side.RED, UnitType.TANK, 1000.0, 1700.0));
        units.add(MovementKit.attacking(12, Side.RED, UnitType.TANK, 1200.0, 1200.0));
        return units;
    }

    private static String runScenario(List<UnitData> units, int ticks) {
        NavGrid nav = new FakeNavGrid(100, 20.0);
        PathPlanner planner = FakePlanner.straight();
        Terrain terrain = MovementKit.flatTerrain(nav, planner);
        WeatherModel weather = FakeWeather.clear();
        StandardMovementSystem system = new StandardMovementSystem();
        for (int tick = 1; tick <= ticks; tick++) {
            system.step(tick, units, terrain, weather);
        }
        List<String> dump = new ArrayList<>();
        units.stream()
                .sorted(java.util.Comparator.comparingInt(UnitData::id))
                .forEach(u -> dump.add(u.id() + "|" + u.x() + "|" + u.z() + "|"
                        + u.heading() + "|" + u.odometer() + "|" + u.state()));
        return String.join("\n", dump);
    }
}