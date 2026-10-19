package ua.battlesim.movement;

import java.util.List;

import ua.battlesim.api.NavGrid;
import ua.battlesim.api.PathPlanner;
import ua.battlesim.api.Side;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.WeatherModel;

/** Shared test helpers: unit builders and tick loops. */
final class MovementKit {

    private MovementKit() {
    }

    /**
     * A ready unit with full health and no cooldown (heading and aim 0 rad).
     *
     * @return the unit
     */
    static UnitData unit(int id, Side side, UnitType type, double x, double z) {
        return new UnitData(id, side, type, x, z, 0.0, 0.0, 100, UnitState.READY, 0, 0.0, -1);
    }

    /**
     * An engaging unit (state ATTACKING): the movement system must leave it
     * alone, so it acts as a stationary obstacle/target in tests.
     *
     * @return the unit
     */
    static UnitData attacking(int id, Side side, UnitType type, double x, double z) {
        UnitData unit = unit(id, side, type, x, z);
        unit.setState(UnitState.ATTACKING);
        return unit;
    }

    /** @return the flat 2000 m fake terrain with the given nav grid and planner */
    static Terrain flatTerrain(NavGrid nav, PathPlanner planner) {
        return FakeTerrain.flat(nav, planner);
    }

    /** Runs the given number of ticks (1-based) on a fresh system. */
    static void run(StandardMovementSystem system, int ticks, List<UnitData> units, Terrain t, WeatherModel w) {
        for (int tick = 1; tick <= ticks; tick++) {
            system.step(tick, units, t, w);
        }
    }

    /** @return the euclidean distance between two units in metres */
    static double distance(UnitData a, UnitData b) {
        return StrictMath.hypot(a.x() - b.x(), a.z() - b.z());
    }
}