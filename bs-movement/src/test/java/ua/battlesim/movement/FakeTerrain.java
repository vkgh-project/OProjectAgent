package ua.battlesim.movement;

import java.util.List;

import ua.battlesim.api.Bridge;
import ua.battlesim.api.LineOfSight;
import ua.battlesim.api.NavGrid;
import ua.battlesim.api.PathPlanner;
import ua.battlesim.api.Prop;
import ua.battlesim.api.Road;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.Vec2;
import ua.battlesim.api.WaterField;

/**
 * Test fake of the terrain module: configurable size and height grade
 * (heightAt = grade x z), no water, all line of sight clear; the navigation
 * grid and path planner are injected fakes.
 */
final class FakeTerrain implements Terrain {

    private final double size;
    private final double grade;
    private final NavGrid nav;
    private final PathPlanner planner;

    FakeTerrain(double size, double grade, NavGrid nav, PathPlanner planner) {
        this.size = size;
        this.grade = grade;
        this.nav = nav;
        this.planner = planner;
    }

    /** @return a flat 2000 m world with the given nav grid and planner */
    static FakeTerrain flat(NavGrid nav, PathPlanner planner) {
        return new FakeTerrain(2000.0, 0.0, nav, planner);
    }

    @Override
    public double size() {
        return size;
    }

    @Override
    public double heightAt(double x, double z) {
        return grade * z;
    }

    @Override
    public WaterField water() {
        return FAKE_WATER;
    }

    @Override
    public NavGrid nav() {
        return nav;
    }

    @Override
    public LineOfSight los() {
        return (from, eyeFrom, to, eyeTo) -> true;
    }

    @Override
    public PathPlanner planner() {
        return planner;
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

    private static final WaterField FAKE_WATER = new WaterField() {
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
}