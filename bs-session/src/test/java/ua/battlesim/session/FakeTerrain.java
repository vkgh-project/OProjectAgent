package ua.battlesim.session;

import java.util.List;

import ua.battlesim.api.Bridge;
import ua.battlesim.api.LineOfSight;
import ua.battlesim.api.NavGrid;
import ua.battlesim.api.PathPlanner;
import ua.battlesim.api.Prop;
import ua.battlesim.api.Road;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.WaterField;

/** Minimal deterministic terrain fake: flat, no water, clear line of sight. */
final class FakeTerrain implements Terrain {

    private final double size;

    private FakeTerrain(double size) {
        this.size = size;
    }

    /** @return a flat 2000 m world */
    static FakeTerrain standard() {
        return new FakeTerrain(2000.0);
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
        return null;
    }

    @Override
    public NavGrid nav() {
        return null;
    }

    @Override
    public LineOfSight los() {
        return (from, eyeFrom, to, eyeTo) -> true;
    }

    @Override
    public PathPlanner planner() {
        return null;
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
}