package ua.battlesim.features.internal;

import ua.battlesim.api.HeightField;
import ua.battlesim.api.Prop;
import ua.battlesim.api.WaterField;
import ua.battlesim.features.RoadCorridor;
import ua.battlesim.math.DetRandom;
import ua.battlesim.math.PerlinNoise;
import ua.battlesim.math.Smooth;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Places trees (noise-driven dense forest plus a coarser scatter) and rocks
 * (slope-biased clusters), keeping everything outside the road corridor and off
 * water.
 *
 * <p>Determinism: candidates are visited in a fixed grid order and every random
 * decision comes from the supplied {@link DetRandom}; the noise field is built
 * from the same seed, so the placement depends only on the fields and the seed.
 */
public final class Props {

    private static final double TWO_PI = 2.0 * StrictMath.PI;
    private static final double TREE_SPACING = 13.0;
    private static final double SCATTER_SPACING = 60.0;
    private static final double TREE_CLEAR_WATER = 0.1;
    private static final double TREE_SWAMP = 0.5;
    private static final double TREE_MAX_SLOPE = 0.5;
    private static final double ROCK_WATER = 0.1;

    private Props() {
        throw new AssertionError("Props is a static utility class");
    }

    /** The placed trees and rocks (both immutable). */
    public record Placed(List<Prop> trees, List<Prop> rocks) {
        public Placed {
            Objects.requireNonNull(trees, "trees");
            Objects.requireNonNull(rocks, "rocks");
            trees = List.copyOf(trees);
            rocks = List.copyOf(rocks);
        }
    }

    /**
     * Places trees and rocks.
     *
     * @param h        height field, non-null
     * @param w        water field, non-null
     * @param noise    deterministic noise field (from the same seed), non-null
     * @param corridor road-corridor mask (props are kept outside it), non-null
     * @param mapSize  map side length in metres, finite, {@code > 0}
     * @param rng      deterministic generator (its state is advanced), non-null
     * @return the placed trees and rocks
     */
    public static Placed place(HeightField h, WaterField w, PerlinNoise noise,
            RoadCorridor corridor, double mapSize, DetRandom rng) {
        Objects.requireNonNull(h, "h");
        Objects.requireNonNull(w, "w");
        Objects.requireNonNull(noise, "noise");
        Objects.requireNonNull(corridor, "corridor");
        Objects.requireNonNull(rng, "rng");
        if (!Double.isFinite(mapSize) || mapSize <= 0.0) {
            throw new IllegalArgumentException("mapSize must be finite and > 0: " + mapSize);
        }
        double mean = meanHeight(h, mapSize);
        List<Prop> trees = new ArrayList<>();
        denseForest(h, w, noise, corridor, mapSize, mean, rng, trees);
        scatteredTrees(h, w, corridor, mapSize, mean, rng, trees);
        List<Prop> rocks = new ArrayList<>();
        rockClusters(h, w, corridor, mapSize, rng, rocks);
        return new Placed(trees, rocks);
    }

    /** Dense forest: a jittered 13 m grid where smoothstep noise density is high. */
    private static void denseForest(HeightField h, WaterField w, PerlinNoise noise,
            RoadCorridor corridor, double mapSize, double mean, DetRandom rng, List<Prop> out) {
        int cols = (int) StrictMath.ceil(mapSize / TREE_SPACING);
        for (int gz = 0; gz < cols; gz++) {
            for (int gx = 0; gx < cols; gx++) {
                double x = gx * TREE_SPACING + rng.nextDouble() * TREE_SPACING;
                double z = gz * TREE_SPACING + rng.nextDouble() * TREE_SPACING;
                if (x < 0 || x >= mapSize || z < 0 || z >= mapSize) {
                    continue;
                }
                double density = Smooth.smoothstep(0.45, 0.75,
                        (noise.fbm(x / 120.0, z / 120.0, 3, 2.0, 0.5) + 1.0) * 0.5);
                if (density < 0.60) {
                    continue;
                }
                if (!plantableTree(h, w, corridor, x, z, mapSize)) {
                    continue;
                }
                int kind = h.heightAt(x, z) > mean ? 1 : 0; // conifer at altitude
                double scale = 0.8 + 0.6 * density;
                out.add(new Prop(x, z, scale, rng.nextDouble() * TWO_PI, rng.nextDouble(), kind));
            }
        }
    }

    /** Scattered trees: a coarser 60 m grid, kept with a fixed probability. */
    private static void scatteredTrees(HeightField h, WaterField w, RoadCorridor corridor,
            double mapSize, double mean, DetRandom rng, List<Prop> out) {
        int cols = (int) StrictMath.ceil(mapSize / SCATTER_SPACING);
        for (int gz = 0; gz < cols; gz++) {
            for (int gx = 0; gx < cols; gx++) {
                double x = gx * SCATTER_SPACING + rng.nextDouble() * SCATTER_SPACING;
                double z = gz * SCATTER_SPACING + rng.nextDouble() * SCATTER_SPACING;
                if (x < 0 || x >= mapSize || z < 0 || z >= mapSize) {
                    continue;
                }
                if (rng.nextDouble() > 0.35) {
                    continue;
                }
                if (!plantableTree(h, w, corridor, x, z, mapSize)) {
                    continue;
                }
                int kind = h.heightAt(x, z) > mean ? 1 : 0;
                double scale = 0.9 + 0.4 * rng.nextDouble();
                out.add(new Prop(x, z, scale, rng.nextDouble() * TWO_PI, rng.nextDouble(), kind));
            }
        }
    }

    /** Rocks: clusters with more rocks on steeper ground, scale 0.5-2.4. */
    private static void rockClusters(HeightField h, WaterField w, RoadCorridor corridor,
            double mapSize, DetRandom rng, List<Prop> out) {
        int nClusters = 6 + rng.nextInt(6);
        for (int c = 0; c < nClusters; c++) {
            double cx = rng.nextDouble() * mapSize;
            double cz = rng.nextDouble() * mapSize;
            int nRocks = 2 + (int) StrictMath.round(slopeAt(h, cx, cz, mapSize) * 15.0);
            if (nRocks < 2) {
                nRocks = 2;
            }
            if (nRocks > 14) {
                nRocks = 14;
            }
            for (int r = 0; r < nRocks; r++) {
                double a = rng.nextDouble() * TWO_PI;
                double rad = rng.nextDouble() * 18.0;
                double x = cx + StrictMath.cos(a) * rad;
                double z = cz + StrictMath.sin(a) * rad;
                if (x < 0 || x >= mapSize || z < 0 || z >= mapSize) {
                    continue;
                }
                if (corridor.inCorridor(x, z)) {
                    continue;
                }
                if (w.depthAt(x, z) > ROCK_WATER) {
                    continue;
                }
                double scale = 0.5 + rng.nextDouble() * 1.9;
                int kind = scale > 1.5 ? 1 : 0;
                out.add(new Prop(x, z, scale, rng.nextDouble() * TWO_PI, rng.nextDouble(), kind));
            }
        }
    }

    private static boolean plantableTree(HeightField h, WaterField w, RoadCorridor corridor,
            double x, double z, double mapSize) {
        if (corridor.inCorridor(x, z)) {
            return false;
        }
        if (w.depthAt(x, z) > TREE_CLEAR_WATER) {
            return false;
        }
        if (w.swampAt(x, z) > TREE_SWAMP) {
            return false;
        }
        if (slopeAt(h, x, z, mapSize) > TREE_MAX_SLOPE) {
            return false;
        }
        return true;
    }

    private static double slopeAt(HeightField h, double x, double z, double mapSize) {
        double e = 5.0;
        double gx = (h.heightAt(clamp(x + e, 0, mapSize), z)
                - h.heightAt(clamp(x - e, 0, mapSize), z)) / (2.0 * e);
        double gz = (h.heightAt(x, clamp(z + e, 0, mapSize))
                - h.heightAt(x, clamp(z - e, 0, mapSize))) / (2.0 * e);
        return StrictMath.sqrt(gx * gx + gz * gz);
    }

    private static double meanHeight(HeightField h, double mapSize) {
        int n = 64;
        double sum = 0.0;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                sum += h.heightAt((i + 0.5) * mapSize / n, (j + 0.5) * mapSize / n);
            }
        }
        return sum / (n * n);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}