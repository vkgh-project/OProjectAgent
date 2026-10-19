package ua.battlesim.terrain;

import ua.battlesim.api.SimConfig;
import ua.battlesim.api.Vec2;
import ua.battlesim.math.DetRandom;
import ua.battlesim.math.FloatGrid;
import ua.battlesim.math.PerlinNoise;
import ua.battlesim.math.Polylines;
import ua.battlesim.terrain.internal.AStar;
import ua.battlesim.terrain.internal.InternalHeightField;
import ua.battlesim.terrain.internal.InternalWaterField;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Generates a deterministic {@link TerrainBase} from a seed and configuration.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Height grid from layered fbm (large relief, hills, fine detail)
 *       minus narrow ravines from ridged noise</li>
 *   <li>1-2 rivers: coarse A* preferring downhill, Chaikin smoothing,
 *       monotone water level, carved channel with sloping banks, flow vectors</li>
 *   <li>1-2 lakes on the flattest of 40 candidate sites (bowl carving,
 *       level = lowest rim)</li>
 *   <li>Swamps on low, flat areas (mask plus shallow water)</li>
 *   <li>Baked terrain shadows by marching toward a random sun
 *       (elevation 26-56 degrees)</li>
 * </ol>
 *
 * <p>Determinism: the same seed and configuration always produce the same
 * terrain. No wall clock, no unseeded randomness, StrictMath for trigonometry.
 *
 * <p>Thread-safety: the generator is stateless (all methods are static);
 * the returned {@link TerrainBase} is immutable after generation.
 */
public final class TerrainGenerator {

    private static final int GRID_SIZE = 512;

    private TerrainGenerator() {
        throw new AssertionError("TerrainGenerator is a static utility class");
    }

    public static TerrainBase generate(long seed, SimConfig cfg) {
        Objects.requireNonNull(cfg, "cfg");
        double mapSize = cfg.mapSize();
        double cellSize = mapSize / GRID_SIZE;

        DetRandom rng = new DetRandom(seed);
        PerlinNoise noise = new PerlinNoise(seed);

        FloatGrid heights = new FloatGrid(GRID_SIZE, GRID_SIZE);
        generateBaseHeights(noise, cellSize, heights);

        FloatGrid depth = new FloatGrid(GRID_SIZE, GRID_SIZE);
        FloatGrid flowX = new FloatGrid(GRID_SIZE, GRID_SIZE);
        FloatGrid flowZ = new FloatGrid(GRID_SIZE, GRID_SIZE);
        FloatGrid swamp = new FloatGrid(GRID_SIZE, GRID_SIZE);

        int numRivers = 1 + (rng.nextBoolean() ? 1 : 0);
        for (int r = 0; r < numRivers; r++) {
            generateRiver(rng, heights, depth, flowX, flowZ, mapSize, cellSize);
        }

        int numLakes = 1 + (rng.nextBoolean() ? 1 : 0);
        generateLakes(rng, heights, depth, mapSize, cellSize, numLakes);

        generateSwamps(noise, heights, swamp, cellSize);

        FloatGrid forest = new FloatGrid(GRID_SIZE, GRID_SIZE);
        generateForest(noise, cellSize, forest, depth);

        double[] sunDir = generateSunDirection(rng);
        FloatGrid shade = new FloatGrid(GRID_SIZE, GRID_SIZE);
        bakeShadows(heights, sunDir, mapSize, cellSize, shade);

        InternalHeightField heightField = new InternalHeightField(mapSize, cellSize, heights);
        InternalWaterField waterField = new InternalWaterField(cellSize, depth, flowX, flowZ, swamp);

        return new TerrainBase(heightField, waterField, forest, shade, sunDir);
    }

    static List<double[]> riverLevels(long seed, SimConfig cfg) {
        Objects.requireNonNull(cfg, "cfg");
        double mapSize = cfg.mapSize();
        double cellSize = mapSize / GRID_SIZE;
        DetRandom rng = new DetRandom(seed);
        PerlinNoise noise = new PerlinNoise(seed);

        FloatGrid heights = new FloatGrid(GRID_SIZE, GRID_SIZE);
        generateBaseHeights(noise, cellSize, heights);

        FloatGrid depth = new FloatGrid(GRID_SIZE, GRID_SIZE);
        FloatGrid flowX = new FloatGrid(GRID_SIZE, GRID_SIZE);
        FloatGrid flowZ = new FloatGrid(GRID_SIZE, GRID_SIZE);

        List<double[]> riverLevelLists = new ArrayList<>();
        int numRivers = 1 + (rng.nextBoolean() ? 1 : 0);
        for (int r = 0; r < numRivers; r++) {
            double[] levels = generateRiver(rng, heights, depth, flowX, flowZ, mapSize, cellSize);
            if (levels != null) {
                riverLevelLists.add(levels);
            }
        }
        return riverLevelLists;
    }

    private static void generateBaseHeights(PerlinNoise noise, double cellSize, FloatGrid heights) {
        for (int j = 0; j < GRID_SIZE; j++) {
            for (int i = 0; i < GRID_SIZE; i++) {
                double x = (i + 0.5) * cellSize;
                double z = (j + 0.5) * cellSize;
                double large = noise.fbm(x / 1100.0, z / 1100.0, 4, 2.0, 0.5) * 55.0;
                double hills = noise.fbm(x / 300.0, z / 300.0, 4, 2.0, 0.5) * 16.0;
                double fine = noise.fbm(x / 80.0, z / 80.0, 3, 2.0, 0.5) * 4.0;
                double r = noise.ridged(x / 250.0, z / 250.0, 3, 2.5, 0.5);
                double ravine = 0.0;
                double threshold = 0.65;
                if (r > threshold) {
                    ravine = ((r - threshold) / (1.0 - threshold)) * 9.0;
                }
                double h = large + hills + fine - ravine;
                heights.set(i, j, (float) h);
            }
        }
    }

    private static double[] generateRiver(DetRandom rng, FloatGrid heights,
            FloatGrid depth, FloatGrid flowX, FloatGrid flowZ,
            double mapSize, double cellSize) {

        int coarseSize = 100;
        double coarseCell = mapSize / coarseSize;
        int startEdge = rng.nextInt(4);
        int endEdge = (startEdge + 2) % 4;
        int startPos = rng.nextInt(coarseSize);
        int endPos = rng.nextInt(coarseSize);

        int startI, startJ, endI, endJ;
        switch (startEdge) {
            case 0 -> { startI = startPos; startJ = 0; }
            case 1 -> { startI = coarseSize - 1; startJ = startPos; }
            case 2 -> { startI = startPos; startJ = coarseSize - 1; }
            default -> { startI = 0; startJ = startPos; }
        }
        switch (endEdge) {
            case 0 -> { endI = endPos; endJ = 0; }
            case 1 -> { endI = coarseSize - 1; endJ = endPos; }
            case 2 -> { endI = endPos; endJ = coarseSize - 1; }
            default -> { endI = 0; endJ = endPos; }
        }

        float[] coarseHeights = new float[coarseSize * coarseSize];
        for (int j = 0; j < coarseSize; j++) {
            for (int i = 0; i < coarseSize; i++) {
                int hi = clamp((int) (i * GRID_SIZE / (double) coarseSize), 0, GRID_SIZE - 1);
                int hj = clamp((int) (j * GRID_SIZE / (double) coarseSize), 0, GRID_SIZE - 1);
                coarseHeights[j * coarseSize + i] = heights.get(hi, hj);
            }
        }

        List<int[]> path = AStar.findPath(coarseSize, coarseHeights, startI, startJ, endI, endJ);
        if (path == null || path.size() < 2) {
            return null;
        }

        List<Vec2> pathWorld = new ArrayList<>(path.size());
        for (int[] cell : path) {
            pathWorld.add(new Vec2((cell[0] + 0.5) * coarseCell, (cell[1] + 0.5) * coarseCell));
        }

        List<Vec2> smooth = Polylines.chaikin(pathWorld, 2);
        int n = smooth.size();
        double[] waterLevels = new double[n];
        waterLevels[0] = terrainAt(heights, cellSize, smooth.get(0).x(), smooth.get(0).z()) - 2.0;
        for (int i = 1; i < n; i++) {
            double h = terrainAt(heights, cellSize, smooth.get(i).x(), smooth.get(i).z());
            waterLevels[i] = StrictMath.min(h - 2.0, waterLevels[i - 1]);
        }

        double halfWidth = 9.0 + rng.nextDouble() * 7.0;
        double maxDepth = 3.0;

        for (int i = 0; i < n - 1; i++) {
            Vec2 a = smooth.get(i);
            Vec2 b = smooth.get(i + 1);
            double wLevel = (waterLevels[i] + waterLevels[i + 1]) * 0.5;

            int minI = clamp((int) StrictMath.floor((StrictMath.min(a.x(), b.x()) - halfWidth) / cellSize), 0, GRID_SIZE - 1);
            int maxI = clamp((int) StrictMath.ceil((StrictMath.max(a.x(), b.x()) + halfWidth) / cellSize), 0, GRID_SIZE - 1);
            int minJ = clamp((int) StrictMath.floor((StrictMath.min(a.z(), b.z()) - halfWidth) / cellSize), 0, GRID_SIZE - 1);
            int maxJ = clamp((int) StrictMath.ceil((StrictMath.max(a.z(), b.z()) + halfWidth) / cellSize), 0, GRID_SIZE - 1);

            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double segLen = StrictMath.sqrt(dx * dx + dz * dz);
            double dirX = segLen > 1e-9 ? dx / segLen : 1.0;
            double dirZ = segLen > 1e-9 ? dz / segLen : 0.0;

            for (int gj = minJ; gj <= maxJ; gj++) {
                for (int gi = minI; gi <= maxI; gi++) {
                    double cellX = (gi + 0.5) * cellSize;
                    double cellZ = (gj + 0.5) * cellSize;
                    Vec2 p = new Vec2(cellX, cellZ);
                    double dist = Polylines.distanceToSegment(p, a, b);
                    if (dist >= halfWidth) continue;
                    double t = dist / halfWidth;
                    double channelDepth = maxDepth * (1.0 - t * t);
                    double targetH = wLevel - channelDepth;
                    double currentH = heights.get(gi, gj);
                    if (targetH < currentH) {
                        heights.set(gi, gj, (float) targetH);
                    }
                    double d = StrictMath.max(0.0, wLevel - heights.get(gi, gj));
                    depth.set(gi, gj, (float) d);
                    double flowSpeed = 0.5 * d;
                    flowX.set(gi, gj, (float) (dirX * flowSpeed));
                    flowZ.set(gi, gj, (float) (dirZ * flowSpeed));
                }
            }
        }
        return waterLevels;
    }


    private static void generateLakes(DetRandom rng, FloatGrid heights, FloatGrid depth,
            double mapSize, double cellSize, int numLakes) {

        List<double[]> candidates = new ArrayList<>(40);
        int sampleRadius = 15;
        for (int c = 0; c < 40; c++) {
            double cx = mapSize * (0.15 + rng.nextDouble() * 0.70);
            double cz = mapSize * (0.15 + rng.nextDouble() * 0.70);
            int ci = (int) (cx / cellSize);
            int cj = (int) (cz / cellSize);
            double sum = 0.0, sumSq = 0.0;
            int count = 0;
            for (int dj = -sampleRadius; dj <= sampleRadius; dj++) {
                for (int di = -sampleRadius; di <= sampleRadius; di++) {
                    int gi = ci + di, gj = cj + dj;
                    if (gi < 0 || gi >= GRID_SIZE || gj < 0 || gj >= GRID_SIZE) continue;
                    float h = heights.get(gi, gj);
                    sum += h; sumSq += h * h; count++;
                }
            }
            if (count == 0) continue;
            double mean = sum / count;
            double variance = StrictMath.max(0.0, sumSq / count - mean * mean);
            double flatness = 1.0 / (1.0 + StrictMath.sqrt(variance));
            candidates.add(new double[]{cx, cz, flatness});
        }

        candidates.sort((a, b) -> {
            int cmp = Double.compare(b[2], a[2]);
            if (cmp != 0) return cmp;
            cmp = Double.compare(a[0], b[0]);
            if (cmp != 0) return cmp;
            return Double.compare(a[1], b[1]);
        });

        int lakesToPlace = StrictMath.min(numLakes, candidates.size());
        for (int L = 0; L < lakesToPlace; L++) {
            double lakeX = candidates.get(L)[0];
            double lakeZ = candidates.get(L)[1];
            double lakeRadius = 30.0 + rng.nextDouble() * 30.0;
            int rCells = (int) StrictMath.ceil(lakeRadius / cellSize) + 1;
            int ci = (int) (lakeX / cellSize);
            int cj = (int) (lakeZ / cellSize);

            double rimHeight = Double.POSITIVE_INFINITY;
            for (int dj = -rCells; dj <= rCells; dj++) {
                for (int di = -rCells; di <= rCells; di++) {
                    int gi = ci + di, gj = cj + dj;
                    if (gi < 0 || gi >= GRID_SIZE || gj < 0 || gj >= GRID_SIZE) continue;
                    double dist = StrictMath.sqrt((di * cellSize) * (di * cellSize)
                            + (dj * cellSize) * (dj * cellSize));
                    if (dist >= lakeRadius * 0.9 && dist <= lakeRadius * 1.1) {
                        rimHeight = StrictMath.min(rimHeight, heights.get(gi, gj));
                    }
                }
            }
            if (!Double.isFinite(rimHeight)) continue;
            double waterLevel = rimHeight - 0.5;

            for (int dj = -rCells; dj <= rCells; dj++) {
                for (int di = -rCells; di <= rCells; di++) {
                    int gi = ci + di, gj = cj + dj;
                    if (gi < 0 || gi >= GRID_SIZE || gj < 0 || gj >= GRID_SIZE) continue;
                    double dist = StrictMath.sqrt((di * cellSize) * (di * cellSize)
                            + (dj * cellSize) * (dj * cellSize));
                    if (dist >= lakeRadius) continue;
                    double t = dist / lakeRadius;
                    double bowlDepth = 2.0 * (1.0 - t * t);
                    double targetH = waterLevel - bowlDepth;
                    double currentH = heights.get(gi, gj);
                    if (targetH < currentH) heights.set(gi, gj, (float) targetH);
                    double d = StrictMath.max(0.0, waterLevel - heights.get(gi, gj));
                    depth.set(gi, gj, (float) d);
                }
            }
        }
    }


    private static void generateSwamps(PerlinNoise noise, FloatGrid heights,
            FloatGrid swamp, double cellSize) {
        float[] allH = heights.values();
        float[] sorted = allH.clone();
        Arrays.sort(sorted);
        float median = sorted[sorted.length / 2];

        for (int j = 0; j < GRID_SIZE; j++) {
            for (int i = 0; i < GRID_SIZE; i++) {
                double x = (i + 0.5) * cellSize;
                double z = (j + 0.5) * cellSize;
                double swampNoise = (noise.fbm(x / 200.0, z / 200.0, 3, 2.0, 0.5) + 1.0) * 0.5;
                float h = heights.get(i, j);
                float hL = i > 0 ? heights.get(i - 1, j) : h;
                float hR = i < GRID_SIZE - 1 ? heights.get(i + 1, j) : h;
                float hU = j > 0 ? heights.get(i, j - 1) : h;
                float hD = j < GRID_SIZE - 1 ? heights.get(i, j + 1) : h;
                double slope = (StrictMath.abs(h - hL) + StrictMath.abs(h - hR)
                        + StrictMath.abs(h - hU) + StrictMath.abs(h - hD)) * 0.25;
                double val = 0.0;
                if (h < median && slope < 1.0 && swampNoise > 0.3) {
                    val = ((swampNoise - 0.3) / 0.7) * 0.8;
                }
                swamp.set(i, j, (float) StrictMath.min(1.0, val));
            }
        }
    }

    private static void generateForest(PerlinNoise noise, double cellSize,
            FloatGrid forest, FloatGrid depth) {
        for (int j = 0; j < GRID_SIZE; j++) {
            for (int i = 0; i < GRID_SIZE; i++) {
                double x = (i + 0.5) * cellSize;
                double z = (j + 0.5) * cellSize;
                double forestNoise = (noise.fbm(x / 150.0, z / 150.0, 3, 2.0, 0.5) + 1.0) * 0.5;
                double d = depth.get(i, j);
                double waterPenalty = d > 0.5 ? 0.3 : 1.0;
                forest.set(i, j, (float) (forestNoise * waterPenalty * 0.7));
            }
        }
    }

    private static double[] generateSunDirection(DetRandom rng) {
        double elevation = StrictMath.toRadians(26.0 + rng.nextDouble() * 30.0);
        double azimuth = rng.nextDouble() * 2.0 * StrictMath.PI;
        double sx = StrictMath.cos(elevation) * StrictMath.cos(azimuth);
        double sy = StrictMath.sin(elevation);
        double sz = StrictMath.cos(elevation) * StrictMath.sin(azimuth);
        double len = StrictMath.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-12) len = 1.0;
        return new double[]{sx / len, sy / len, sz / len};
    }

    private static void bakeShadows(FloatGrid heights, double[] sunDir,
            double mapSize, double cellSize, FloatGrid shade) {
        double marchStep = 5.0;
        double maxMarch = 200.0;
        for (int j = 0; j < GRID_SIZE; j++) {
            for (int i = 0; i < GRID_SIZE; i++) {
                double x = (i + 0.5) * cellSize;
                double z = (j + 0.5) * cellSize;
                double h = heights.get(i, j);
                boolean shadowed = false;
                for (double dist = marchStep; dist <= maxMarch; dist += marchStep) {
                    double mx = x + sunDir[0] * dist;
                    double mz = z + sunDir[2] * dist;
                    double rayY = h + sunDir[1] * dist;
                    if (mx < 0 || mx >= mapSize || mz < 0 || mz >= mapSize) break;
                    int mi = (int) StrictMath.floor(mx / cellSize);
                    int mj = (int) StrictMath.floor(mz / cellSize);
                    if (mi < 0 || mi >= GRID_SIZE || mj < 0 || mj >= GRID_SIZE) break;
                    if (heights.get(mi, mj) > rayY) { shadowed = true; break; }
                }
                shade.set(i, j, shadowed ? 0.0f : 1.0f);
            }
        }
    }

    private static double terrainAt(FloatGrid heights, double cellSize, double x, double z) {
        return heights.bilinear(x / cellSize, z / cellSize);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}

