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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A small, fully deterministic {@link Terrain} for the demo and for unit tests.
 *
 * <p>It is not a real terrain generator: it is a fixed world (sine-wave height,
 * one circular pond, a band of swamp, two roads, one bridge and a handful of
 * trees and rocks) whose values depend only on coordinates, so it is
 * deterministic and safe to use as a fake of the bs-terrain module.
 */
public final class DemoTerrain implements Terrain {

    private static final double SIZE = 400.0;
    private static final double POND_X = 150.0;
    private static final double POND_Z = 150.0;
    private static final double POND_RADIUS = 60.0;

    private final List<Road> roads;
    private final List<Bridge> bridges;
    private final List<Prop> trees;
    private final List<Prop> rocks;

    /** Creates the fixed demo world. */
    public DemoTerrain() {
        this.roads = buildRoads();
        this.bridges = buildBridges();
        this.trees = buildTrees();
        this.rocks = buildRocks();
    }

    private static List<Road> buildRoads() {
        Road r1 = new Road(
                List.of(new Vec2(0.0, 220.0), new Vec2(150.0, 150.0), new Vec2(400.0, 120.0)),
                6.0, 0,
                new boolean[] { false, true, false });
        Road r2 = new Road(
                List.of(new Vec2(240.0, 0.0), new Vec2(250.0, 200.0), new Vec2(240.0, 400.0)),
                5.0, 1,
                new boolean[] { false, false, false });
        return List.of(r1, r2);
    }

    private static List<Bridge> buildBridges() {
        Bridge b = new Bridge(new Vec2(90.0, 150.0), new Vec2(210.0, 150.0), 2.0, 8.0);
        return List.of(b);
    }

    private static List<Prop> buildTrees() {
        List<Prop> list = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            double x = (i * 97) % 400;
            double z = (i * 53 + 10) % 400;
            if (StrictMath.hypot(x - POND_X, z - POND_Z) < POND_RADIUS) {
                continue;
            }
            double scale = 0.7 + 0.5 * ((i * 31) % 10) / 10.0;
            list.add(new Prop(x, z, scale, i * 0.4, (i % 5) / 5.0, i % 3));
        }
        return List.copyOf(list);
    }

    private static List<Prop> buildRocks() {
        List<Prop> list = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            double x = (i * 61 + 5) % 400;
            double z = (i * 43 + 20) % 400;
            if (StrictMath.hypot(x - POND_X, z - POND_Z) < POND_RADIUS) {
                continue;
            }
            double scale = 0.5 + 0.6 * ((i * 17) % 10) / 10.0;
            list.add(new Prop(x, z, scale, i * 0.5, (i % 4) / 4.0, i % 2));
        }
        return List.copyOf(list);
    }

    @Override
    public double size() {
        return SIZE;
    }

    @Override
    public double heightAt(double x, double z) {
        return 6.0 * StrictMath.sin(x * 0.02) * StrictMath.cos(z * 0.025)
                + 3.0 * StrictMath.sin((x + z) * 0.03)
                + 1.0 * StrictMath.cos(x * 0.05);
    }

    @Override
    public WaterField water() {
        return new WaterField() {
            @Override
            public double depthAt(double x, double z) {
                double d = StrictMath.hypot(x - POND_X, z - POND_Z);
                return Math.max(0.0, (POND_RADIUS - d) * 0.15);
            }

            @Override
            public Vec2 flowAt(double x, double z) {
                return depthAt(x, z) > 0.0 ? new Vec2(0.3, 0.1) : new Vec2(0.0, 0.0);
            }

            @Override
            public double swampAt(double x, double z) {
                return Math.max(0.0, 0.6 - StrictMath.abs(z - 320.0) / 80.0);
            }
        };
    }

    @Override
    public NavGrid nav() {
        double cell = SIZE / 40.0;
        return new NavGrid() {
            @Override
            public int cells() {
                return 40;
            }

            @Override
            public double cellSize() {
                return cell;
            }

            @Override
            public boolean passable(int i, int j) {
                return water().depthAt(i * cell, j * cell) == 0.0;
            }

            @Override
            public int component(int i, int j) {
                return 0;
            }

            @Override
            public int mainComponent() {
                return 0;
            }

            @Override
            public boolean walkable(double x, double z, double radius) {
                return water().depthAt(x, z) == 0.0;
            }

            @Override
            public boolean isRoad(double x, double z) {
                return false;
            }
        };
    }

    @Override
    public LineOfSight los() {
        return (from, eyeFrom, to, eyeTo) -> true;
    }

    @Override
    public PathPlanner planner() {
        return (from, to, type, radius) -> Optional.of(List.of(from, to));
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
        double[] v = { 0.5, 0.8, 0.3 };
        double len = StrictMath.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[] { v[0] / len, v[1] / len, v[2] / len };
    }

    @Override
    public double shadeAt(double x, double z) {
        return 0.85 + 0.15 * StrictMath.sin(x * 0.03);
    }
}