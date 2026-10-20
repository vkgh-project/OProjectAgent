package ua.battlesim.gfx.units.internal;

import ua.battlesim.api.Bridge;
import ua.battlesim.api.LineOfSight;
import ua.battlesim.api.NavGrid;
import ua.battlesim.api.PathPlanner;
import ua.battlesim.api.Prop;
import ua.battlesim.api.Road;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.WaterField;

import java.util.List;

/**
 * A deterministic fake terrain for the unit tests. Only {@code heightAt}
 * (configurable as {@code base + slopeX*x + slopeZ*z}) and {@code sunDirection}
 * are meaningful; the other {@link Terrain} members are unused by the renderer
 * and return null/empty.
 */
final class TestTerrain implements Terrain {

    double base = 0.0;
    double slopeX = 0.0;
    double slopeZ = 0.0;
    double[] sun = { 0.45, 0.80, 0.35 };

    @Override
    public double size() {
        return 2000.0;
    }

    @Override
    public double heightAt(double x, double z) {
        return base + slopeX * x + slopeZ * z;
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
        return null;
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
        return sun;
    }

    @Override
    public double shadeAt(double x, double z) {
        return 1.0;
    }
}
