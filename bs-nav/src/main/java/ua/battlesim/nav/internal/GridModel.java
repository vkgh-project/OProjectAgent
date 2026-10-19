package ua.battlesim.nav.internal;

import ua.battlesim.api.HeightField;
import ua.battlesim.api.Prop;
import ua.battlesim.api.Road;
import ua.battlesim.api.Vec2;
import ua.battlesim.api.WaterField;
import ua.battlesim.nav.FeatureSet;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The static 128x128 navigation model shared by {@link NavGridImpl},
 * {@link LineOfSightImpl} and {@link PathPlannerImpl}.
 *
 * <p>Cell blocking rules:
 * <ul>
 *   <li>water depth above 0.45 m at the cell centre;</li>
 *   <li>any swamp (swamp factor above 0) at the cell centre;</li>
 *   <li>cell slope above 32 degrees (average gradient over the cell);</li>
 *   <li>any tree inside the cell;</li>
 *   <li>a rock with scale &gt;= 1.4 inside the cell.</li>
 * </ul>
 * Road cells (cell centre within half the road width of a road centre line)
 * are always passable, whatever the other conditions.
 *
 * <p>Passable cells are grouped into 8-neighbour connected components
 * without corner cutting. If no component connects the north quarter
 * ({@code j < cells/4}) to the south quarter ({@code j >= 3*cells/4}), a
 * 3-cell-wide vertical fallback corridor in the map centre is carved and
 * the components are recomputed, which guarantees a north-south route.
 *
 * <p>Clearance penalty of a passable non-road cell in {@code [0, 1]}:
 * {@code 0.7 * max(0, slope - 15) / 17 + 0.3 * min(1, depth / 0.45)},
 * clamped to {@code [0, 1]}; road and corridor cells have penalty 0.
 *
 * <p>Determinism: pure function of its inputs; no randomness, no wall
 * clock, StrictMath for trigonometry, plain arrays only (no hash-order
 * dependence), fixed iteration order everywhere.
 *
 * <p>Immutable and thread-safe after construction.
 */
final class GridModel {

    static final int CELLS = 128;
    static final double WATER_BLOCK_DEPTH = 0.45;
    static final double MAX_SLOPE_DEGREES = 32.0;
    static final double SLOPE_PENALTY_FLOOR_DEGREES = 15.0;
    static final double ROCK_BLOCK_MIN_SCALE = 1.4;
    static final double TREE_OCCLUDER_RADIUS = 10.0;
    static final double LOS_SAMPLE_STEP = 6.0;
    static final double LOS_END_IGNORE = 10.0;
    static final double ROAD_FACTOR_VEHICLE = 0.6;
    static final double ROAD_FACTOR_INFANTRY = 0.8;
    static final double BLOCKED_NEIGHBOR_PENALTY = 0.2;

    private static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] DJ = {0, 1, 1, 1, 0, -1, -1, -1};

    final double mapSize;
    final double cellSize;
    private final HeightField height;

    final boolean[] passable = new boolean[CELLS * CELLS];
    final int[] component = new int[CELLS * CELLS];
    final double[] penalty = new double[CELLS * CELLS];
    final boolean[] road = new boolean[CELLS * CELLS];
    final int[] blockedNeighbors = new int[CELLS * CELLS];
    final int mainComponent;

    // per-cell neighbour table for A*: neighbour index or -1, base edge cost
    final int[][] neighbor = new int[CELLS * CELLS][8];
    final double[][] edgeCost = new double[CELLS * CELLS][8];

    private final boolean[] blocked = new boolean[CELLS * CELLS];
    private final boolean[] corridor = new boolean[CELLS * CELLS];
    private final double[] penaltyBase = new double[CELLS * CELLS];

    // road geometry (flattened segments) for isRoad queries
    private final double[] segAx;
    private final double[] segAz;
    private final double[] segBx;
    private final double[] segBz;
    private final double[] segHalfWidth;

    final TreeGrid treeGrid;

    GridModel(HeightField height, WaterField water, FeatureSet features) {
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(water, "water");
        Objects.requireNonNull(features, "features");
        this.height = height;
        double size = height.size();
        if (!Double.isFinite(size) || size <= 0.0) {
            throw new IllegalArgumentException("map size must be finite and > 0 (metres): " + size);
        }
        this.mapSize = size;
        this.cellSize = size / CELLS;

        markBlockedByTerrain(water);
        markBlockedByProps(features);
        double[][] roadSegs = collectRoadSegments(features.roads());
        markRoads(roadSegs);
        this.segAx = roadSegs[0];
        this.segAz = roadSegs[1];
        this.segBx = roadSegs[2];
        this.segBz = roadSegs[3];
        this.segHalfWidth = roadSegs[4];

        applyPassability();
        computeComponents();
        if (!hasNorthSouthRoute()) {
            carveFallbackCorridor();
            applyPassability();
            computeComponents();
        }
        this.mainComponent = computeMainComponent();
        precomputeBlockedNeighbors();
        precomputeEdges();
        this.treeGrid = new TreeGrid(mapSize, features.trees());
    }

    /** @return the cell index of a point in map coordinates (point inside the map) */
    int cellIndex(double x, double z) {
        int i = (int) (x / cellSize);
        if (i >= CELLS) {
            i = CELLS - 1;
        }
        int j = (int) (z / cellSize);
        if (j >= CELLS) {
            j = CELLS - 1;
        }
        return j * CELLS + i;
    }

    double centerX(int idx) {
        return (idx % CELLS + 0.5) * cellSize;
    }

    double centerZ(int idx) {
        return (idx / CELLS + 0.5) * cellSize;
    }

    /**
     * Whether a circle of the given radius around (x, z) fits entirely on
     * passable cells inside the map.
     */
    boolean walkable(double x, double z, double radius) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite: (" + x + ", " + z + ")");
        }
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("radius must be finite and >= 0 (metres): " + radius);
        }
        if (x < radius || x > mapSize - radius || z < radius || z > mapSize - radius) {
            return false;
        }
        int i0 = clampCell((int) ((x - radius) / cellSize));
        int i1 = clampCell((int) ((x + radius) / cellSize));
        int j0 = clampCell((int) ((z - radius) / cellSize));
        int j1 = clampCell((int) ((z + radius) / cellSize));
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                int idx = j * CELLS + i;
                if (passable[idx]) {
                    continue;
                }
                double dx = x < i * cellSize ? i * cellSize - x
                        : (x > (i + 1) * cellSize ? x - (i + 1) * cellSize : 0.0);
                double dz = z < j * cellSize ? j * cellSize - z
                        : (z > (j + 1) * cellSize ? z - (j + 1) * cellSize : 0.0);
                if (dx * dx + dz * dz <= radius * radius) {
                    return false;
                }
            }
        }
        return true;
    }

    /** @return whether (x, z) in metres lies on a road (within its half width) */
    boolean isRoad(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite: (" + x + ", " + z + ")");
        }
        for (int k = 0; k < segAx.length; k++) {
            double d = distanceToSegment(x, z, segAx[k], segAz[k], segBx[k], segBz[k]);
            if (d <= segHalfWidth[k]) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // construction steps
    // ------------------------------------------------------------------

    private void markBlockedByTerrain(WaterField water) {
        for (int j = 0; j < CELLS; j++) {
            for (int i = 0; i < CELLS; i++) {
                int idx = j * CELLS + i;
                double x0 = i * cellSize;
                double x1 = (i + 1) * cellSize;
                double z0 = j * cellSize;
                double z1 = (j + 1) * cellSize;
                double h00 = height.heightAt(x0, z0);
                double h10 = height.heightAt(x1, z0);
                double h01 = height.heightAt(x0, z1);
                double h11 = height.heightAt(x1, z1);
                double gx = (h11 + h10 - h00 - h01) * 0.5 / cellSize;
                double gz = (h11 + h01 - h00 - h10) * 0.5 / cellSize;
                double slopeDeg = StrictMath.toDegrees(StrictMath.atan(StrictMath.hypot(gx, gz)));

                double cx = (i + 0.5) * cellSize;
                double cz = (j + 0.5) * cellSize;
                double depth = water.depthAt(cx, cz);
                double swamp = water.swampAt(cx, cz);

                blocked[idx] = depth > WATER_BLOCK_DEPTH
                        || swamp > 0.0
                        || slopeDeg > MAX_SLOPE_DEGREES;

                double slopePart = Math.max(0.0, slopeDeg - SLOPE_PENALTY_FLOOR_DEGREES)
                        / (MAX_SLOPE_DEGREES - SLOPE_PENALTY_FLOOR_DEGREES);
                double depthPart = Math.min(1.0, depth / WATER_BLOCK_DEPTH);
                penaltyBase[idx] = clamp01(0.7 * slopePart + 0.3 * depthPart);
            }
        }
    }

    private void markBlockedByProps(FeatureSet features) {
        for (Prop tree : features.trees()) {
            if (tree.x() < 0.0 || tree.x() > mapSize || tree.z() < 0.0 || tree.z() > mapSize) {
                continue;
            }
            blocked[cellIndex(tree.x(), tree.z())] = true;
        }
        for (Prop rock : features.rocks()) {
            if (rock.scale() < ROCK_BLOCK_MIN_SCALE) {
                continue;
            }
            if (rock.x() < 0.0 || rock.x() > mapSize || rock.z() < 0.0 || rock.z() > mapSize) {
                continue;
            }
            blocked[cellIndex(rock.x(), rock.z())] = true;
        }
    }

    /** @return [segAx, segAz, segBx, segBz, segHalfWidth] flattened road geometry */
    private static double[][] collectRoadSegments(List<Road> roads) {
        int count = 0;
        for (Road r : roads) {
            count += Math.max(0, r.points().size() - 1);
        }
        double[] ax = new double[count];
        double[] az = new double[count];
        double[] bx = new double[count];
        double[] bz = new double[count];
        double[] hw = new double[count];
        int k = 0;
        for (Road r : roads) {
            List<Vec2> pts = r.points();
            for (int s = 1; s < pts.size(); s++) {
                ax[k] = pts.get(s - 1).x();
                az[k] = pts.get(s - 1).z();
                bx[k] = pts.get(s).x();
                bz[k] = pts.get(s).z();
                hw[k] = r.width() * 0.5;
                k++;
            }
        }
        return new double[][]{ax, az, bx, bz, hw};
    }

    private void markRoads(double[][] roadSegs) {
        double[] ax = roadSegs[0];
        double[] az = roadSegs[1];
        double[] bx = roadSegs[2];
        double[] bz = roadSegs[3];
        double[] hw = roadSegs[4];
        for (int j = 0; j < CELLS; j++) {
            for (int i = 0; i < CELLS; i++) {
                double cx = (i + 0.5) * cellSize;
                double cz = (j + 0.5) * cellSize;
                for (int k = 0; k < ax.length; k++) {
                    if (distanceToSegment(cx, cz, ax[k], az[k], bx[k], bz[k]) <= hw[k]) {
                        road[j * CELLS + i] = true;
                        break;
                    }
                }
            }
        }
    }

    private void applyPassability() {
        for (int idx = 0; idx < passable.length; idx++) {
            passable[idx] = road[idx] || !blocked[idx];
            penalty[idx] = (road[idx] || corridor[idx]) ? 0.0 : penaltyBase[idx];
        }
    }

    private void carveFallbackCorridor() {
        int mid = CELLS / 2;
        for (int j = 0; j < CELLS; j++) {
            for (int i = mid - 1; i <= mid + 1; i++) {
                int idx = j * CELLS + i;
                blocked[idx] = false;
                corridor[idx] = true;
            }
        }
    }

    private void computeComponents() {
        Arrays.fill(component, 0);
        int[] queue = new int[CELLS * CELLS];
        int next = 1;
        for (int idx = 0; idx < component.length; idx++) {
            if (!passable[idx] || component[idx] != 0) {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = idx;
            component[idx] = next;
            while (head < tail) {
                int cur = queue[head++];
                int ci = cur % CELLS;
                int cj = cur / CELLS;
                for (int d = 0; d < 8; d++) {
                    int ni = ci + DX[d];
                    int nj = cj + DJ[d];
                    if (ni < 0 || ni >= CELLS || nj < 0 || nj >= CELLS) {
                        continue;
                    }
                    int nIdx = nj * CELLS + ni;
                    if (!passable[nIdx] || component[nIdx] != 0) {
                        continue;
                    }
                    if (DX[d] != 0 && DJ[d] != 0
                            && (!passable[cj * CELLS + ci + DX[d]] || !passable[(cj + DJ[d]) * CELLS + ci])) {
                        continue; // no corner cutting
                    }
                    component[nIdx] = next;
                    queue[tail++] = nIdx;
                }
            }
            next++;
        }
    }

    /** @return the id of the largest component (smallest id wins ties) */
    private int computeMainComponent() {
        int[] sizes = new int[CELLS * CELLS + 1];
        for (int c : component) {
            if (c > 0) {
                sizes[c]++;
            }
        }
        int best = -1;
        int bestSize = -1;
        for (int c = 1; c < sizes.length; c++) {
            if (sizes[c] > bestSize) {
                bestSize = sizes[c];
                best = c;
            }
        }
        return best;
    }

    private boolean hasNorthSouthRoute() {
        boolean[] north = new boolean[CELLS * CELLS + 1];
        boolean[] south = new boolean[CELLS * CELLS + 1];
        for (int idx = 0; idx < component.length; idx++) {
            int c = component[idx];
            if (c == 0) {
                continue;
            }
            int j = idx / CELLS;
            if (j < CELLS / 4) {
                north[c] = true;
            } else if (j >= 3 * CELLS / 4) {
                south[c] = true;
            }
        }
        for (int c = 1; c < north.length; c++) {
            if (north[c] && south[c]) {
                return true;
            }
        }
        return false;
    }

    private void precomputeBlockedNeighbors() {
        for (int j = 0; j < CELLS; j++) {
            for (int i = 0; i < CELLS; i++) {
                int count = 0;
                for (int d = 0; d < 8; d++) {
                    int ni = i + DX[d];
                    int nj = j + DJ[d];
                    if (ni < 0 || ni >= CELLS || nj < 0 || nj >= CELLS) {
                        continue;
                    }
                    if (!passable[nj * CELLS + ni]) {
                        count++;
                    }
                }
                blockedNeighbors[j * CELLS + i] = count;
            }
        }
    }

    private void precomputeEdges() {
        for (int idx = 0; idx < passable.length; idx++) {
            int ci = idx % CELLS;
            int cj = idx / CELLS;
            double cx = centerX(idx);
            double cz = centerZ(idx);
            for (int d = 0; d < 8; d++) {
                neighbor[idx][d] = -1;
                int ni = ci + DX[d];
                int nj = cj + DJ[d];
                if (ni < 0 || ni >= CELLS || nj < 0 || nj >= CELLS) {
                    continue;
                }
                int nIdx = nj * CELLS + ni;
                if (!passable[nIdx]) {
                    continue;
                }
                if (DX[d] != 0 && DJ[d] != 0
                        && (!passable[cj * CELLS + ci + DX[d]] || !passable[(cj + DJ[d]) * CELLS + ci])) {
                    continue; // no corner cutting
                }
                double dist = StrictMath.hypot(centerX(nIdx) - cx, centerZ(nIdx) - cz);
                neighbor[idx][d] = nIdx;
                edgeCost[idx][d] = dist * (1.0 + penalty[nIdx])
                        + BLOCKED_NEIGHBOR_PENALTY * blockedNeighbors[nIdx];
            }
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static int clampCell(int v) {
        return v < 0 ? 0 : (v >= CELLS ? CELLS - 1 : v);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static double distanceToSegment(double px, double pz,
                                            double ax, double az, double bx, double bz) {
        double abx = bx - ax;
        double abz = bz - az;
        double len2 = abx * abx + abz * abz;
        if (len2 == 0.0) {
            double dx = px - ax;
            double dz = pz - az;
            return StrictMath.sqrt(dx * dx + dz * dz);
        }
        double t = ((px - ax) * abx + (pz - az) * abz) / len2;
        if (t < 0.0) {
            t = 0.0;
        } else if (t > 1.0) {
            t = 1.0;
        }
        double dx = px - (ax + abx * t);
        double dz = pz - (az + abz * t);
        return StrictMath.sqrt(dx * dx + dz * dz);
    }
}
