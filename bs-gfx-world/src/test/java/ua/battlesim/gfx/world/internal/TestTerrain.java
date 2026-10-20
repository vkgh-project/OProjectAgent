package ua.battlesim.gfx.world.internal;

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

/**
 * A minimal, configurable fake {@link Terrain} for the unit tests.
 *
 * <p>Only the pieces the mesh builders read (size, height, water, roads,
 * bridges, props, shade) are modelled; the navigation / LOS / planner methods
 * are not used by bs-gfx-world and throw if touched.
 *
 * <p>{@code waterMode}: {@code 0} = dry, {@code 1} = flooded everywhere,
 * {@code 2} = water only where {@code x < size/2}.
 */
public final class TestTerrain implements Terrain {

    public double size = 100.0;
    public double height = 0.0;
    public int waterMode = 0;
    public double swamp = 0.0;
    public double shade = 1.0;
    public List<Prop> trees = List.of();
    public List<Prop> rocks = List.of();
    public List<Road> roads = List.of();
    public List<Bridge> bridges = List.of();

    @Override
    public double size() {
        return size;
    }

    @Override
    public double heightAt(double x, double z) {
        return height;
    }

    @Override
    public WaterField water() {
        return new WaterField() {
            @Override
            public double depthAt(double x, double z) {
                switch (TestTerrain.this.waterMode) {
                    case 1:
                        return 3.0;
                    case 2:
                        return x < TestTerrain.this.size / 2.0 ? 3.0 : 0.0;
                    default:
                        return 0.0;
                }
            }

            @Override
            public Vec2 flowAt(double x, double z) {
                return new Vec2(0.2, 0.1);
            }

            @Override
            public double swampAt(double x, double z) {
                return TestTerrain.this.swamp;
            }
        };
    }

    @Override
    public NavGrid nav() {
        throw new UnsupportedOperationException("not used by bs-gfx-world");
    }

    @Override
    public LineOfSight los() {
        throw new UnsupportedOperationException("not used by bs-gfx-world");
    }

    @Override
    public PathPlanner planner() {
        throw new UnsupportedOperationException("not used by bs-gfx-world");
    }

    @Override
    public List<Road> roads() {
        return roads;
    }

    @Override
    public List<Bridge> bridges() {
        return bridges;
    }

    @Override
    public List<Prop> trees() {
        return trees;
    }

    @Override
    public List<Prop> rocks() {
        return rocks;
    }

    @Override
    public double[] sunDirection() {
        return new double[] { 0.0, 1.0, 0.0 };
    }

    @Override
    public double shadeAt(double x, double z) {
        return shade;
    }
}