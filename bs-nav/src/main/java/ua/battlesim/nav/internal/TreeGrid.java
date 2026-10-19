package ua.battlesim.nav.internal;

import ua.battlesim.api.Prop;

import java.util.ArrayList;
import java.util.List;

/**
 * Spatial index over the placed trees, used by the line-of-sight service to
 * find 10 m occluders near a sample point without scanning every tree.
 *
 * <p>Bucket grid of 20 m cells over the map (plus one ring of clamped
 * buckets so trees just outside the edge still answer queries). Iteration
 * order is the order of the input tree list, so results are deterministic.
 *
 * <p>Immutable and thread-safe after construction.
 */
final class TreeGrid {

    private static final double BUCKET_METRES = 20.0;

    private final int bucketsPerSide;
    private final double[] treeX;
    private final double[] treeZ;
    private final int[][] buckets;

    TreeGrid(double mapSize, List<Prop> trees) {
        this.bucketsPerSide = (int) StrictMath.ceil(mapSize / BUCKET_METRES) + 1;
        int nBuckets = bucketsPerSide * bucketsPerSide;
        List<List<Integer>> tmp = new ArrayList<>(nBuckets);
        for (int b = 0; b < nBuckets; b++) {
            tmp.add(new ArrayList<>(2));
        }
        this.treeX = new double[trees.size()];
        this.treeZ = new double[trees.size()];
        for (int k = 0; k < trees.size(); k++) {
            Prop t = trees.get(k);
            treeX[k] = t.x();
            treeZ[k] = t.z();
            int bi = clamp((int) StrictMath.floor(t.x() / BUCKET_METRES));
            int bj = clamp((int) StrictMath.floor(t.z() / BUCKET_METRES));
            tmp.get(bj * bucketsPerSide + bi).add(k);
        }
        this.buckets = new int[nBuckets][];
        for (int b = 0; b < nBuckets; b++) {
            List<Integer> list = tmp.get(b);
            int[] arr = new int[list.size()];
            for (int k = 0; k < list.size(); k++) {
                arr[k] = list.get(k);
            }
            buckets[b] = arr;
        }
    }

    /** @return whether a tree lies strictly closer than {@code radius} metres to (x, z) */
    boolean within(double x, double z, double radius) {
        int i0 = clamp((int) StrictMath.floor((x - radius) / BUCKET_METRES));
        int i1 = clamp((int) StrictMath.floor((x + radius) / BUCKET_METRES));
        int j0 = clamp((int) StrictMath.floor((z - radius) / BUCKET_METRES));
        int j1 = clamp((int) StrictMath.floor((z + radius) / BUCKET_METRES));
        double r2 = radius * radius;
        for (int bj = j0; bj <= j1; bj++) {
            for (int bi = i0; bi <= i1; bi++) {
                for (int k : buckets[bj * bucketsPerSide + bi]) {
                    double dx = treeX[k] - x;
                    double dz = treeZ[k] - z;
                    if (dx * dx + dz * dz < r2) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int clamp(int v) {
        return v < 0 ? 0 : (v >= bucketsPerSide ? bucketsPerSide - 1 : v);
    }
}
