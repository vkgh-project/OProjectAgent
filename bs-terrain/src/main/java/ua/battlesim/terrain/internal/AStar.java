package ua.battlesim.terrain.internal;

import java.util.*;

/**
 * Deterministic A* pathfinding on a 2-D grid, preferring downhill paths.
 *
 * <p>Thread-safety: the method is pure (no mutable state); safe to call
 * from any thread.
 */
public final class AStar {

    public AStar() {
        throw new AssertionError("AStar is a static utility class");
    }

    /**
     * Finds a path from (sx, sy) to (tx, ty) on the given height grid,
     * preferring downhill routes.
     *
     * @param n       grid size (n x n)
     * @param heights height at each cell (row-major, n x n)
     * @param sx      start column
     * @param sy      start row
     * @param tx      target column
     * @param ty      target row
     * @return the path as a list of [col, row] pairs, or null if no path exists
     * @throws IllegalArgumentException on invalid arguments
     */
    public static List<int[]> findPath(int n, float[] heights, int sx, int sy, int tx, int ty) {
        if (n < 1 || heights == null || heights.length != n * n) {
            throw new IllegalArgumentException("invalid grid: n=" + n);
        }
        if (sx < 0 || sx >= n || sy < 0 || sy >= n || tx < 0 || tx >= n || ty < 0 || ty >= n) {
            throw new IllegalArgumentException("coordinates out of range: (" + sx + "," + sy + ") -> (" + tx + "," + ty + ")");
        }

        double[] gScore = new double[n * n];
        Arrays.fill(gScore, Double.POSITIVE_INFINITY);
        boolean[] closed = new boolean[n * n];
        int[] parent = new int[n * n];
        Arrays.fill(parent, -1);

        int startIdx = sy * n + sx;
        int targetIdx = ty * n + tx;
        gScore[startIdx] = 0.0;

        PriorityQueue<Node> pq = new PriorityQueue<>();
        pq.add(new Node(startIdx, h(sx, sy, tx, ty)));

        final int[] dx = {1, -1, 0, 0};
        final int[] dy = {0, 0, 1, -1};

        while (!pq.isEmpty()) {
            Node top = pq.poll();
            int idx = top.idx();

            if (idx == targetIdx) {
                List<int[]> path = new ArrayList<>();
                int cur = idx;
                while (cur != -1) {
                    path.add(new int[]{cur % n, cur / n});
                    cur = parent[cur];
                }
                Collections.reverse(path);
                return path;
            }

            if (closed[idx]) {
                continue;
            }
            closed[idx] = true;

            int cx = idx % n;
            int cy = idx / n;

            for (int d = 0; d < 4; d++) {
                int nx = cx + dx[d];
                int ny = cy + dy[d];
                if (nx < 0 || nx >= n || ny < 0 || ny >= n) {
                    continue;
                }
                int nidx = ny * n + nx;
                if (closed[nidx]) {
                    continue;
                }

                double climb = StrictMath.max(0.0, heights[nidx] - heights[idx]);
                double cost = 1.0 + climb * 0.5;
                double newG = gScore[idx] + cost;

                if (newG < gScore[nidx]) {
                    gScore[nidx] = newG;
                    parent[nidx] = idx;
                    double f = newG + h(nx, ny, tx, ty);
                    pq.add(new Node(nidx, f));
                }
            }
        }

        return null;
    }

    private static double h(int x, int y, int tx, int ty) {
        return StrictMath.abs(x - tx) + StrictMath.abs(y - ty);
    }

    public record Node(int idx, double f) implements Comparable<Node> {
        @Override
        public int compareTo(Node other) {
            int cmp = Double.compare(this.f, other.f);
            return cmp != 0 ? cmp : Integer.compare(this.idx, other.idx);
        }
    }
}
