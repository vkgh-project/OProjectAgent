package ua.battlesim.features.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Deterministic 8-directional A* path finder over a cost grid.
 *
 * <p>The grid is {@code width x height}; a cell index is {@code row * width +
 * col}. A cell is impassable when its cost is {@link Double#POSITIVE_INFINITY};
 * otherwise the cost (which must be {@code > 0}) is paid when the cell is
 * entered, scaled by the step length (1 for orthogonal, {@code sqrt(2)} for
 * diagonal moves). Diagonal moves never cut through a corner that is blocked
 * on either side.
 *
 * <p>Determinism: the priority queue is keyed by {@code (f-cost, unique
 * insertion order)}, which is a total order, so the set of expanded cells —
 * and therefore the returned path — is identical for identical inputs. All
 * state lives in plain arrays indexed by cell; no HashMap or HashSet iteration
 * order is ever consulted.
 */
public final class AStar {

    private static final double SQRT2 = StrictMath.sqrt(2.0);
    private static final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] DY = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final double[] STEP = {SQRT2, 1.0, SQRT2, 1.0, 1.0, SQRT2, 1.0, SQRT2};

    private AStar() {
        throw new AssertionError("AStar is a static utility class");
    }

    /**
     * Finds a path from {@code (sx, sy)} to {@code (gx, gy)}.
     *
     * @param w    grid width, {@code >= 1}
     * @param h    grid height, {@code >= 1}
     * @param cost per-cell cost, length {@code w * h}, each entry {@code > 0}
     *             or {@link Double#POSITIVE_INFINITY} (impassable)
     * @param sx   start column
     * @param sy   start row
     * @param gx   goal column
     * @param gy   goal row
     * @return the cell indices from start to goal inclusive, or an empty list
     *         when no path exists
     * @throws IllegalArgumentException on an invalid argument
     */
    public static List<Integer> findPath(int w, int h, double[] cost, int sx, int sy, int gx, int gy) {
        if (w < 1 || h < 1) {
            throw new IllegalArgumentException("grid must be >= 1: " + w + "x" + h);
        }
        int n = w * h;
        if (cost == null || cost.length != n) {
            throw new IllegalArgumentException("cost length must be " + n
                    + " but is " + (cost == null ? -1 : cost.length));
        }
        if (sx < 0 || sx >= w || sy < 0 || sy >= h || gx < 0 || gx >= w || gy < 0 || gy >= h) {
            throw new IllegalArgumentException("start/goal out of bounds for " + w + "x" + h);
        }
        int start = sy * w + sx;
        int goal = gy * w + gx;

        double[] g = new double[n];
        Arrays.fill(g, Double.POSITIVE_INFINITY);
        int[] came = new int[n];
        Arrays.fill(came, -1);
        boolean[] closed = new boolean[n];

        record Node(double f, double gCost, int idx, long order) {
        }
        PriorityQueue<Node> pq = new PriorityQueue<>((a, b) -> {
            int c = Double.compare(a.f(), b.f());
            return c != 0 ? c : Long.compare(a.order(), b.order());
        });
        long order = 0L;
        g[start] = 0.0;
        pq.add(new Node(heuristic(sx, sy, gx, gy), 0.0, start, order++));

        while (!pq.isEmpty()) {
            Node cur = pq.poll();
            if (closed[cur.idx()]) {
                continue;
            }
            if (cur.gCost() > g[cur.idx()]) {
                continue; // stale entry
            }
            closed[cur.idx()] = true;
            if (cur.idx() == goal) {
                break;
            }
            int cx = cur.idx() % w;
            int cy = cur.idx() / w;
            for (int d = 0; d < 8; d++) {
                int nx = cx + DX[d];
                int ny = cy + DY[d];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
                    continue;
                }
                int nidx = ny * w + nx;
                if (closed[nidx]) {
                    continue;
                }
                double cellCost = cost[nidx];
                if (Double.isInfinite(cellCost)) {
                    continue;
                }
                if (DX[d] != 0 && DY[d] != 0) {
                    // no corner cutting through a blocked orthogonal neighbour
                    if (Double.isInfinite(cost[cy * w + nx]) || Double.isInfinite(cost[ny * w + cx])) {
                        continue;
                    }
                }
                double ng = cur.gCost() + STEP[d] * cellCost;
                if (ng < g[nidx]) {
                    g[nidx] = ng;
                    came[nidx] = cur.idx();
                    pq.add(new Node(ng + heuristic(nx, ny, gx, gy), ng, nidx, order++));
                }
            }
        }

        if (!closed[goal]) {
            return List.of();
        }
        List<Integer> path = new ArrayList<>();
        for (int c = goal; c != -1; c = came[c]) {
            path.add(c);
        }
        Collections.reverse(path);
        return path;
    }

    /** Euclidean distance in cell units — an admissible, consistent heuristic. */
    private static double heuristic(int x, int y, int gx, int gy) {
        double dx = x - gx;
        double dy = y - gy;
        return StrictMath.sqrt(dx * dx + dy * dy);
    }
}