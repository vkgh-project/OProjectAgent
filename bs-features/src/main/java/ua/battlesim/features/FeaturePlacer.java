package ua.battlesim.features;

import ua.battlesim.api.HeightField;
import ua.battlesim.api.Road;
import ua.battlesim.api.Vec2;
import ua.battlesim.api.WaterField;
import ua.battlesim.features.internal.Props;
import ua.battlesim.features.internal.RoadPlanner;
import ua.battlesim.math.DetRandom;
import ua.battlesim.math.PerlinNoise;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic placer of roads, bridges, trees and rocks (the bs-features
 * public API).
 *
 * <p>{@link #place(HeightField, WaterField, long)} takes a {@link HeightField}
 * and {@link WaterField} (both bs-api contracts) plus a 64-bit seed and
 * returns a {@link TerrainFeatures} holding the road polylines (with
 * per-segment bridge flags), the bridges that cross water, the placed trees,
 * the placed rocks and the road-corridor mask that keeps props off the roads.
 *
 * <p>Algorithm: roads are found with A* over a 128x128 cost grid (slope, water
 * and swamp penalties; cells already holding a road cost x0.35), then Chaikin
 * smoothing and 5 m resampling; bridges mark runs where water depth exceeds
 * 0.25 m (extended by 3 samples per side, deck 0.8 m above the water surface).
 * Trees come from a jittered 13 m noise-driven forest plus a 60 m scatter;
 * rocks come from slope-biased clusters (scale 0.5-2.4).
 *
 * <p>Determinism: the same (heightField, waterField, seed) always yields an
 * equal {@link TerrainFeatures}. No wall clock, no unseeded randomness,
 * StrictMath for trigonometry, and candidates are processed in a fixed grid
 * order so that hash/iteration order never affects the result.
 *
 * <p>Thread-safety: the method is pure with respect to its arguments; the
 * returned {@link TerrainFeatures} is immutable.
 */
public final class FeaturePlacer {

    /** Road-corridor mask resolution (cells per map side). */
    private static final int CORRIDOR_CELLS = 256;
    /** Keep-out half-width painted around each road centre line (metres). */
    private static final double CORRIDOR_CLEARANCE = 10.0;

    private FeaturePlacer() {
        throw new AssertionError("FeaturePlacer is a static utility class");
    }

    /**
     * Places roads, bridges, trees, rocks and the road-corridor mask.
     *
     * @param h  the terrain height field (non-null), map size read from {@code h.size()}
     * @param w  the water field (depth, flow, swamp), non-null
     * @param seed the 64-bit deterministic seed
     * @return an immutable, deterministic {@link TerrainFeatures}
     * @throws IllegalArgumentException if the map size is not finite and positive
     */
    public static TerrainFeatures place(HeightField h, WaterField w, long seed) {
        Objects.requireNonNull(h, "h");
        Objects.requireNonNull(w, "w");
        double mapSize = h.size();
        if (!Double.isFinite(mapSize) || mapSize <= 0.0) {
            throw new IllegalArgumentException("mapSize must be finite and > 0: " + mapSize);
        }

        DetRandom rng = new DetRandom(seed);
        PerlinNoise noise = new PerlinNoise(seed);

        // 1. roads (deterministic order: main, west-east, branches, trails) + bridges
        RoadPlanner.Roads roads = RoadPlanner.plan(h, w, rng);
        List<Road> roadList = roads.roads();
        if (roadList.isEmpty()) {
            throw new IllegalStateException("road planning produced no roads");
        }

        // 2. road-corridor mask derived from the roads
        RoadCorridor corridor = buildCorridor(mapSize, roadList);

        // 3. trees and rocks, kept outside the corridor
        Props.Placed placed = Props.place(h, w, noise, corridor, mapSize, rng);

        return new TerrainFeatures(roadList, roads.bridges(), placed.trees(), placed.rocks(), corridor);
    }

    /**
     * Builds the road-corridor mask by painting a keep-out radius around every
     * road centre point.
     */
    private static RoadCorridor buildCorridor(double mapSize, List<Road> roads) {
        int cells = CORRIDOR_CELLS;
        double cs = mapSize / cells;
        boolean[] mask = new boolean[cells * cells];
        int radius = (int) Math.max(1L, StrictMath.ceil(CORRIDOR_CLEARANCE / cs));
        for (Road road : roads) {
            for (Vec2 p : road.points()) {
                int ci = clampCell((int) (p.x() / cs), cells);
                int cj = clampCell((int) (p.z() / cs), cells);
                for (int dj = -radius; dj <= radius; dj++) {
                    int jj = clampCell(cj + dj, cells);
                    for (int di = -radius; di <= radius; di++) {
                        int ii = clampCell(ci + di, cells);
                        mask[jj * cells + ii] = true;
                    }
                }
            }
        }
        return new RoadCorridor(mapSize, cells, mask);
    }

    private static int clampCell(int v, int cells) {
        return v < 0 ? 0 : v >= cells ? cells - 1 : v;
    }
}