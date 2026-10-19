package ua.battlesim.features.internal;

import ua.battlesim.api.Bridge;
import ua.battlesim.api.HeightField;
import ua.battlesim.api.Road;
import ua.battlesim.api.Vec2;
import ua.battlesim.api.WaterField;
import ua.battlesim.math.DetRandom;
import ua.battlesim.math.Polylines;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Plans the road network (main north-south road, west-east road, branches and
 * trails) using A* over a 128x128 cost grid, then derives the bridges from the
 * resampled road polylines.
 *
 * <p>Determinism: the planning order is fixed (main, west-east, branches,
 * trails) and all randomness comes from the supplied {@link DetRandom}; the A*
 * itself is deterministic, so the result depends only on the fields and the
 * seed.
 */
public final class RoadPlanner {

    private static final int GRID = 128;

    private static final double MAIN_WIDTH = 6.5;
    private static final double SECONDARY_WIDTH = 5.0;
    private static final double MINOR_WIDTH = 2.4;

    private static final double SLOPE_COST = 4.0;
    private static final double WATER_COST = 12.0;
    private static final double SWAMP_COST = 6.0;
    private static final double ROAD_DISCOUNT = 0.35;

    private static final double BRIDGE_DEPTH = 0.25;
    private static final int BRIDGE_EXTEND = 3;
    private static final double BRIDGE_DECK = 0.8;

    private static final int CHAIKIN_ITERS = 2;
    private static final double RESAMPLE_STEP = 5.0;

    private RoadPlanner() {
        throw new AssertionError("RoadPlanner is a static utility class");
    }

    /** The planned roads and their bridges (both immutable, deterministic order). */
    public record Roads(List<Road> roads, List<Bridge> bridges) {
        public Roads {
            Objects.requireNonNull(roads, "roads");
            Objects.requireNonNull(bridges, "bridges");
            roads = List.copyOf(roads);
            bridges = List.copyOf(bridges);
        }
    }

    /**
     * Plans the full road network and its bridges.
     *
     * @param h   height field, non-null
     * @param w   water field, non-null
     * @param rng deterministic generator (its state is advanced), non-null
     * @return the roads (main first) and the bridges
     */
    public static Roads plan(HeightField h, WaterField w, DetRandom rng) {
        Objects.requireNonNull(h, "h");
        Objects.requireNonNull(w, "w");
        Objects.requireNonNull(rng, "rng");
        double mapSize = h.size();
        double cs = mapSize / GRID;
        double[] base = baseCost(h, w, cs, mapSize);
        boolean[] roadMask = new boolean[GRID * GRID];
        List<Road> roads = new ArrayList<>();
        List<Bridge> bridges = new ArrayList<>();

        // 1. main north-south road (always present): north edge (row 0) to south edge
        int mStartCol = 40 + rng.nextInt(48);
        int mGoalCol = 40 + rng.nextInt(48);
        List<Integer> mainPath = run(h, w, cs, base, roadMask, mStartCol, 0, mGoalCol, GRID - 1);
        addRoad(mainPath, cs, MAIN_WIDTH, 0, w, bridges, roads, roadMask);

        // 2. west-east road: west edge (col 0) to east edge (col GRID-1)
        int weRow = 30 + rng.nextInt(68);
        addRoad(run(h, w, cs, base, roadMask, 0, weRow, GRID - 1, weRow),
                cs, SECONDARY_WIDTH, 1, w, bridges, roads, roadMask);

        // 3. branches (1-2), each starting on the main road
        int nBranches = 1 + (rng.nextBoolean() ? 1 : 0);
        for (int k = 0; k < nBranches; k++) {
            int scell = mainPath.get(rng.nextInt(mainPath.size()));
            int scol = scell % GRID;
            int srow = scell / GRID;
            int goal = (scell + 20 + rng.nextInt(40)) % (GRID * GRID);
            addRoad(run(h, w, cs, base, roadMask, scol, srow, goal % GRID, goal / GRID),
                    cs, MINOR_WIDTH, 2, w, bridges, roads, roadMask);
        }

        // 4. trails (3-5), short paths between distant cells
        int nTrails = 3 + rng.nextInt(3);
        for (int k = 0; k < nTrails; k++) {
            int scell = rng.nextInt(GRID * GRID);
            int scol = scell % GRID;
            int srow = scell / GRID;
            int goal = (scell + 10 + rng.nextInt(30)) % (GRID * GRID);
            addRoad(run(h, w, cs, base, roadMask, scol, srow, goal % GRID, goal / GRID),
                    cs, MINOR_WIDTH, 3, w, bridges, roads, roadMask);
        }

        return new Roads(roads, bridges);
    }

    /** Runs A* with the road discount applied to already-planned road cells. */
    private static List<Integer> run(HeightField h, WaterField w, double cs, double[] base,
            boolean[] roadMask, int sx, int sy, int gx, int gy) {
        double[] effective = new double[base.length];
        for (int i = 0; i < base.length; i++) {
            effective[i] = roadMask[i] ? base[i] * ROAD_DISCOUNT : base[i];
        }
        List<Integer> path = AStar.findPath(GRID, GRID, effective, sx, sy, gx, gy);
        if (path.size() < 2) {
            // start == goal: nudge the goal so the segment is non-degenerate
            int ngx = (gx + 4) % GRID;
            path = AStar.findPath(GRID, GRID, effective, sx, sy, ngx, gy);
        }
        return path;
    }

    /** Smooths/resamples a cell path into a {@link Road} and records its bridges. */
    private static void addRoad(List<Integer> path, double cs, double width, int kind,
            WaterField w, List<Bridge> bridges, List<Road> roads, boolean[] roadMask) {
        if (path == null || path.size() < 2) {
            return;
        }
        List<Vec2> pts = new ArrayList<>(path.size());
        for (int cell : path) {
            int i = cell % GRID;
            int j = cell / GRID;
            pts.add(new Vec2((i + 0.5) * cs, (j + 0.5) * cs));
        }
        pts = Polylines.chaikin(pts, CHAIKIN_ITERS);
        pts = Polylines.resample(pts, RESAMPLE_STEP);
        if (pts.size() < 2) {
            return;
        }

        for (int cell : path) {
            roadMask[cell] = true;
        }

        int n = pts.size();
        boolean[] inBridge = new boolean[n];
        int k = 0;
        while (k < n) {
            if (w.depthAt(pts.get(k).x(), pts.get(k).z()) <= BRIDGE_DEPTH) {
                k++;
                continue;
            }
            int a = k;
            while (k < n && w.depthAt(pts.get(k).x(), pts.get(k).z()) > BRIDGE_DEPTH) {
                k++;
            }
            int b = k - 1;
            int s = Math.max(0, a - BRIDGE_EXTEND);
            int e = Math.min(n - 1, b + BRIDGE_EXTEND);
            for (int q = s; q <= e; q++) {
                inBridge[q] = true;
            }
            bridges.add(new Bridge(pts.get(s), pts.get(e), BRIDGE_DECK, width));
        }

        boolean[] onBridge = new boolean[n];
        onBridge[0] = inBridge[0];
        for (int q = 1; q < n; q++) {
            onBridge[q] = inBridge[q] || inBridge[q - 1];
        }
        roads.add(new Road(pts, width, kind, onBridge));
    }

    /** Builds the 128x128 base cost grid (slope, water, swamp penalties). */
    private static double[] baseCost(HeightField h, WaterField w, double cs, double mapSize) {
        double[] cost = new double[GRID * GRID];
        for (int j = 0; j < GRID; j++) {
            for (int i = 0; i < GRID; i++) {
                double x = (i + 0.5) * cs;
                double z = (j + 0.5) * cs;
                double hz = h.heightAt(x, z);
                double hL = h.heightAt(clamp(x - cs, 0, mapSize), z);
                double hR = h.heightAt(clamp(x + cs, 0, mapSize), z);
                double hU = h.heightAt(x, clamp(z - cs, 0, mapSize));
                double hD = h.heightAt(x, clamp(z + cs, 0, mapSize));
                double slope = (StrictMath.abs(hz - hL) + StrictMath.abs(hz - hR)
                        + StrictMath.abs(hz - hU) + StrictMath.abs(hz - hD)) * 0.25 / cs;
                double depth = w.depthAt(x, z);
                double swamp = w.swampAt(x, z);
                cost[j * GRID + i] = 1.0 + slope * SLOPE_COST + depth * WATER_COST + swamp * SWAMP_COST;
            }
        }
        return cost;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}