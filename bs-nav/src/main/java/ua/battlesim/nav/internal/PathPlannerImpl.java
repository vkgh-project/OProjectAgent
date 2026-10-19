package ua.battlesim.nav.internal;

import ua.battlesim.api.PathPlanner;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.Vec2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * bs-api {@link PathPlanner} view over the shared {@link GridModel}.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>empty result when an endpoint is outside the map, when an endpoint
 *       cell is blocked, or when the endpoints lie in different connected
 *       components;</li>
 *   <li>weighted A* over the 8-neighbour passable cells (no corner
 *       cutting). Edge cost = distance x (1 + clearance penalty of the
 *       entered cell), multiplied by the road factor of the entered road
 *       cell (0.6 for vehicles, 0.8 for infantry), plus 0.2 per blocked
 *       neighbour of the entered cell. The heuristic is the straight-line
 *       distance; the open list is a deterministic total-ordered heap;</li>
 *   <li>string pulling: the cell-centre polyline is greedily shortened by
 *       the longest straight jump that keeps the unit circle (given radius)
 *       clear, from {@code from} to {@code to}.</li>
 * </ol>
 *
 * <p>Deterministic: the same inputs always yield the same route; no
 * randomness, no wall clock, no hash-order dependence.
 */
final class PathPlannerImpl implements PathPlanner {

    private static final double CLEAR_SAMPLE_METRES = 0.5;
    private static final double RELAX_EPSILON = 1e-12;

    private final GridModel model;

    PathPlannerImpl(GridModel model) {
        this.model = model;
    }

    @Override
    public Optional<List<Vec2>> plan(Vec2 from, Vec2 to, UnitType type, double radius) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(type, "type");
        if (!Double.isFinite(radius) || radius <= 0.0) {
            throw new IllegalArgumentException("radius must be finite and > 0 (metres): " + radius);
        }
        if (outsideMap(from) || outsideMap(to)) {
            return Optional.empty();
        }
        int start = model.cellIndex(from.x(), from.z());
        int goal = model.cellIndex(to.x(), to.z());
        if (!model.passable[start] || !model.passable[goal]) {
            return Optional.empty();
        }
        if (model.component[start] != model.component[goal]) {
            return Optional.empty(); // different connected components: no path
        }
        double roadFactor = type == UnitType.INFANTRY
                ? GridModel.ROAD_FACTOR_INFANTRY
                : GridModel.ROAD_FACTOR_VEHICLE;
        int[] path = aStar(start, goal, to, roadFactor);
        if (path == null) {
            return Optional.empty();
        }
        return Optional.of(stringPull(from, to, path, radius));
    }

    // ------------------------------------------------------------------
    // weighted A* on the precomputed neighbour table
    // ------------------------------------------------------------------

    private int[] aStar(int start, int goal, Vec2 to, double roadFactor) {
        int n = model.passable.length;
        double[] g = new double[n];
        Arrays.fill(g, Double.POSITIVE_INFINITY);
        int[] came = new int[n];
        Arrays.fill(came, -1);
        boolean[] closed = new boolean[n];
        OpenHeap heap = new OpenHeap(1024);
        long seq = 0;
        g[start] = 0.0;
        heap.push(heuristic(start, to), start, seq++);
        while (!heap.isEmpty()) {
            int cur = heap.pop();
            if (closed[cur]) {
                continue;
            }
            closed[cur] = true;
            if (cur == goal) {
                break;
            }
            double gc = g[cur];
            int[] nbr = model.neighbor[cur];
            double[] cost = model.edgeCost[cur];
            boolean[] road = model.road;
            for (int d = 0; d < 8; d++) {
                int nb = nbr[d];
                if (nb < 0 || closed[nb]) {
                    continue;
                }
                double step = road[nb] ? cost[d] * roadFactor : cost[d];
                double ng = gc + step;
                if (ng + RELAX_EPSILON < g[nb]) {
                    g[nb] = ng;
                    came[nb] = cur;
                    heap.push(ng + heuristic(nb, to), nb, seq++);
                }
            }
        }
        if (!closed[goal]) {
            return null;
        }
        int length = 0;
        for (int c = goal; c != -1; c = came[c]) {
            length++;
        }
        int[] path = new int[length];
        int k = length - 1;
        for (int c = goal; c != -1; c = came[c]) {
            path[k--] = c;
        }
        return path;
    }

    private double heuristic(int idx, Vec2 to) {
        return StrictMath.hypot(model.centerX(idx) - to.x(), model.centerZ(idx) - to.z());
    }

    // ------------------------------------------------------------------
    // string pulling with circle clearance
    // ------------------------------------------------------------------

    private List<Vec2> stringPull(Vec2 from, Vec2 to, int[] path, double radius) {
        List<Vec2> cand = new ArrayList<>(path.length + 1);
        cand.add(from);
        for (int idx : path) {
            Vec2 c = new Vec2(model.centerX(idx), model.centerZ(idx));
            Vec2 last = cand.get(cand.size() - 1);
            if (c.x() != last.x() || c.z() != last.z()) {
                cand.add(c);
            }
        }
        Vec2 last = cand.get(cand.size() - 1);
        if (to.x() != last.x() || to.z() != last.z()) {
            cand.add(to);
        }
        int n = cand.size();
        List<Vec2> result = new ArrayList<>();
        result.add(cand.get(0));
        int i = 0;
        while (i < n - 1) {
            Vec2 a = cand.get(i);
            int jump = -1;
            for (int k = n - 1; k > i; k--) {
                if (clear(a, cand.get(k), radius)) {
                    jump = k;
                    break;
                }
            }
            if (jump < 0) {
                // no clear jump: keep the remaining cell-centre route
                for (int k = i + 1; k < n; k++) {
                    result.add(cand.get(k));
                }
                break;
            }
            result.add(cand.get(jump));
            i = jump;
        }
        return List.copyOf(result);
    }

    /** @return whether the straight segment a-b keeps the unit circle clear */
    private boolean clear(Vec2 a, Vec2 b, double radius) {
        double len = StrictMath.hypot(b.x() - a.x(), b.z() - a.z());
        int steps = (int) StrictMath.ceil(len / CLEAR_SAMPLE_METRES);
        for (int s = 0; s <= steps; s++) {
            double t = len == 0.0 ? 0.0 : (double) s / steps;
            double px = a.x() + (b.x() - a.x()) * t;
            double pz = a.z() + (b.z() - a.z()) * t;
            if (!model.walkable(px, pz, radius)) {
                return false;
            }
        }
        return true;
    }

    private boolean outsideMap(Vec2 p) {
        double size = model.mapSize;
        return p.x() < 0.0 || p.x() > size || p.z() < 0.0 || p.z() > size;
    }
}
