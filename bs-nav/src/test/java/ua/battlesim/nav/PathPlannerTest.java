package ua.battlesim.nav;

import org.junit.jupiter.api.Test;
import ua.battlesim.api.NavGrid;
import ua.battlesim.api.PathPlanner;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.Vec2;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Path-planning tests for bs-nav using the {@link FakeTerrain} fake.
 *
 * <p>Covers the spec requirements: a valid route is produced on an open map,
 * no route is returned across disconnected components, and a typical plan
 * finishes within the 10 ms budget.
 */
class PathPlannerTest {

    private static final double MAP = 2000.0;
    private static final double RADIUS = 2.0;

    private static boolean legIsClear(NavGrid grid, Vec2 a, Vec2 b, double radius) {
        int steps = (int) StrictMath.ceil(StrictMath.hypot(b.x() - a.x(), b.z() - a.z()) / 1.0);
        if (steps < 1) {
            steps = 1;
        }
        for (int s = 0; s <= steps; s++) {
            double t = (double) s / steps;
            double px = a.x() + (b.x() - a.x()) * t;
            double pz = a.z() + (b.z() - a.z()) * t;
            if (!grid.walkable(px, pz, radius)) {
                return false;
            }
        }
        return true;
    }

    @Test
    void openMapProducesAValidRoute() {
        FakeTerrain t = new FakeTerrain(MAP);
        NavBundle bundle = NavService.build(t, t);
        PathPlanner planner = bundle.planner();
        NavGrid grid = bundle.grid();
        Vec2 from = new Vec2(100, 100);
        Vec2 to = new Vec2(1900, 1900);
        Optional<List<Vec2>> path = planner.plan(from, to, UnitType.TANK, RADIUS);
        assertTrue(path.isPresent(), "an open flat map must yield a path");
        List<Vec2> pts = path.get();
        assertFalse(pts.isEmpty(), "the path must contain at least the two endpoints");
        assertEquals(from, pts.get(0), "the route must start at 'from'");
        assertEquals(to, pts.get(pts.size() - 1), "the route must end at 'to'");
        for (Vec2 v : pts) {
            assertTrue(v.x() >= 0.0 && v.x() <= MAP && v.z() >= 0.0 && v.z() <= MAP,
                    "waypoint (" + v.x() + ", " + v.z() + ") must lie inside the map");
        }
        double straight = StrictMath.hypot(to.x() - from.x(), to.z() - from.z());
        double length = 0.0;
        for (int k = 1; k < pts.size(); k++) {
            length += StrictMath.hypot(pts.get(k).x() - pts.get(k - 1).x(),
                    pts.get(k).z() - pts.get(k - 1).z());
        }
        assertTrue(length >= straight - 1e-6,
                "route length " + length + " must not be shorter than the straight line " + straight);
        for (int k = 1; k < pts.size(); k++) {
            assertTrue(legIsClear(grid, pts.get(k - 1), pts.get(k), RADIUS),
                    "leg " + k + " must keep the unit circle of radius " + RADIUS + " clear");
        }
    }

    @Test
    void noPathAcrossDisconnectedComponents() {
        // an annulus of water isolates the central island from the outer field
        FakeTerrain t = new FakeTerrain(MAP).waterRing(1000, 1000, 150, 300);
        NavBundle bundle = NavService.build(t, t);
        PathPlanner planner = bundle.planner();
        NavGrid grid = bundle.grid();
        Vec2 island = new Vec2(1000, 1000);
        Vec2 outside = new Vec2(100, 100);
        double cs = grid.cellSize();
        assertTrue(grid.passable((int) (island.x() / cs), (int) (island.z() / cs)),
                "the island centre must be passable");
        assertFalse(planner.plan(island, outside, UnitType.TANK, RADIUS).isPresent(),
                "points on opposite sides of the water ring must not be connectable");
        assertFalse(planner.plan(outside, island, UnitType.TANK, RADIUS).isPresent(),
                "the connectivity check must be symmetric");
        // positive control: two west-edge points stay in the outer component
        Vec2 outerA = new Vec2(100, 100);
        Vec2 outerB = new Vec2(100, 1900);
        assertTrue(planner.plan(outerA, outerB, UnitType.TANK, RADIUS).isPresent(),
                "two points in the outer component must be connectable");
    }

    @Test
    void typicalPlanFitsTheTenMillisecondBudget() {
        // a central water rectangle forces a real detour around an obstacle
        FakeTerrain t = new FakeTerrain(MAP).water(800, 600, 1200, 1000);
        PathPlanner planner = NavService.build(t, t).planner();
        Vec2 from = new Vec2(100, 1000);
        Vec2 to = new Vec2(1900, 1000);
        // warm up the JIT so the budget measures a hot, representative plan
        for (int i = 0; i < 50; i++) {
            planner.plan(from, to, UnitType.TANK, RADIUS);
        }
        long best = Long.MAX_VALUE;
        for (int i = 0; i < 20; i++) {
            long startNanos = System.nanoTime();
            Optional<List<Vec2>> path = planner.plan(from, to, UnitType.TANK, RADIUS);
            long elapsed = System.nanoTime() - startNanos;
            assertTrue(path.isPresent(), "a detour around the central water must exist");
            if (elapsed < best) {
                best = elapsed;
            }
        }
        long tenMsInNanos = 10L * 1_000_000L;
        assertTrue(best < tenMsInNanos,
                "a typical plan took " + (best / 1_000_000.0) + " ms, budget is 10 ms");
    }
}
