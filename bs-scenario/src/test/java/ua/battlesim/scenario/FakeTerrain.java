package ua.battlesim.scenario;

import ua.battlesim.api.Bridge;
import ua.battlesim.api.LineOfSight;
import ua.battlesim.api.NavGrid;
import ua.battlesim.api.PathPlanner;
import ua.battlesim.api.Prop;
import ua.battlesim.api.Road;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.Vec2;
import ua.battlesim.api.WaterField;

import java.util.List;
import java.util.Optional;

/**
 * A deterministic fake {@link Terrain} for scenario tests: a flat, dry,
 * featureless map with a sun straight up and a fully open nav grid (or one
 * supplied from outside). Only {@link #size()}, {@link #nav()} and the
 * structural methods matter for the scenario under test; all queries return
 * constant, deterministic values.
 */
final class FakeTerrain implements Terrain {

    private static final WaterField DRY_WATER = new WaterField() {
        @Override
        public double depthAt(double x, double z) {
            return 0.0;
        }

        @Override
        public Vec2 flowAt(double x, double z) {
            return new Vec2(0.0, 0.0);
        }

        @Override
        public double swampAt(double x, double z) {
            return 0.0;
        }
    };

    private static final LineOfSight OPEN_LOS = (from, eyeFrom, to, eyeTo) -> true;

    private static final PathPlanner STRAIGHT_PLANNER = (from, to, type, radius) ->
            Optional.of(List.of(from, to));

    private final double size;
    private final NavGrid nav;

    /**
     * @param size the map side length in metres, {@code > 0}
     * @param nav  the navigation grid over the map (non-null)
     */
    FakeTerrain(double size, NavGrid nav) {
        if (size <= 0.0) {
            throw new IllegalArgumentException("size must be > 0: " + size);
        }
        this.size = size;
        this.nav = nav;
    }

    @Override
    public double size() {
        return size;
    }

    @Override
    public double heightAt(double x, double z) {
        return 0.0;
    }

    @Override
    public WaterField water() {
        return DRY_WATER;
    }

    @Override
    public NavGrid nav() {
        return nav;
    }

    @Override
    public LineOfSight los() {
        return OPEN_LOS;
    }

    @Override
    public PathPlanner planner() {
        return STRAIGHT_PLANNER;
    }

    @Override
    public List<Road> roads() {
        return List.of();
    }

    @Override
    public List<Bridge> bridges() {
        return List.of();
    }

    @Override
    public List<Prop> trees() {
        return List.of();
    }

    @Override
    public List<Prop> rocks() {
        return List.of();
    }

    @Override
    public double[] sunDirection() {
        return new double[] { 0.0, 1.0, 0.0 };
    }

    @Override
    public double shadeAt(double x, double z) {
        return 1.0;
    }

    @Override
    public String toString() {
        return "FakeTerrain[" + size + " m]";
    }
}
