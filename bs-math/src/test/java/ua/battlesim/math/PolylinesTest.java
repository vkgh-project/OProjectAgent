package ua.battlesim.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import ua.battlesim.api.Vec2;

class PolylinesTest {

    @Test
    void chaikinKeepsEndpoints() {
        List<Vec2> pts = List.of(new Vec2(0, 0), new Vec2(10, 5), new Vec2(30, 1), new Vec2(40, 40));
        for (int it = 0; it <= 3; it++) {
            List<Vec2> out = Polylines.chaikin(pts, it);
            assertEquals(pts.get(0), out.get(0), "first endpoint must be kept");
            assertEquals(pts.get(pts.size() - 1), out.get(out.size() - 1), "last endpoint must be kept");
        }
    }

    @Test
    void chaikinGrowsAsExpected() {
        List<Vec2> pts = List.of(new Vec2(0, 0), new Vec2(5, 3), new Vec2(12, 8));
        for (int it = 0; it <= 4; it++) {
            int expected = 2 + (pts.size() - 2) * (1 << it);
            assertEquals(expected, Polylines.chaikin(pts, it).size(), "size after " + it + " iterations");
        }
    }

    @Test
    void chaikinOfStraightLineStaysCollinear() {
        List<Vec2> pts = List.of(new Vec2(0, 0), new Vec2(10, 10), new Vec2(20, 20), new Vec2(30, 30));
        List<Vec2> out = Polylines.chaikin(pts, 3);
        for (Vec2 p : out) {
            assertEquals(p.x(), p.z(), 1e-9, "point " + p + " not on the straight line");
        }
    }

    @Test
    void chaikinCutsCorners() {
        // a 90-degree corner at (10, 10): subdivision must stay inside the
        // bounding box and pull the neighbour of the endpoint away from it
        List<Vec2> pts = List.of(new Vec2(0, 0), new Vec2(10, 0), new Vec2(10, 10));
        List<Vec2> out = Polylines.chaikin(pts, 4);
        for (Vec2 p : out) {
            assertTrue(p.x() >= 0 && p.x() <= 10 && p.z() >= 0 && p.z() <= 10,
                    "point outside the bounding box: " + p);
        }
        Vec2 secondLast = out.get(out.size() - 2);
        double away = Math.hypot(10.0 - secondLast.x(), 10.0 - secondLast.z());
        assertTrue(away > 1.0, "corner not smoothed, second-to-last point: " + secondLast);
    }

    @Test
    void chaikinValidatesArguments() {
        assertThrows(IllegalArgumentException.class, () -> Polylines.chaikin(List.of(new Vec2(0, 0)), 1));
        assertThrows(IllegalArgumentException.class, () -> Polylines.chaikin(List.of(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> Polylines.chaikin(List.of(new Vec2(0, 0), new Vec2(1, 1)), -1));
        assertThrows(NullPointerException.class, () -> Polylines.chaikin(null, 1));
    }

    @Test
    void resampleStraightLineSpacingWithinOnePercent() {
        List<Vec2> pts = List.of(new Vec2(0, 0), new Vec2(100, 0));
        double step = 10.0;
        List<Vec2> out = Polylines.resample(pts, step);
        assertEquals(0.0, out.get(0).x(), 0.0);
        assertEquals(new Vec2(100, 0), out.get(out.size() - 1), "last point must be the endpoint");
        for (int i = 1; i < out.size() - 1; i++) {
            double d = dist(out.get(i - 1), out.get(i));
            assertTrue(Math.abs(d - step) <= 0.01 * step, "spacing " + d + " not within 1% of " + step);
        }
    }

    @Test
    void resampleIncludesEndpointWithPartialTail() {
        List<Vec2> pts = List.of(new Vec2(0, 0), new Vec2(95, 0));
        double step = 10.0;
        List<Vec2> out = Polylines.resample(pts, step);
        assertEquals(new Vec2(95, 0), out.get(out.size() - 1), "last point must be the endpoint");
        // 0, 10, ..., 90 plus the endpoint at 95
        assertEquals(11, out.size());
        for (int i = 1; i < out.size() - 1; i++) {
            double d = dist(out.get(i - 1), out.get(i));
            assertTrue(Math.abs(d - step) <= 0.01 * step, "spacing " + d + " not within 1% of " + step);
        }
    }

    @Test
    void resampleOfRectangleStaysOnBoundary() {
        List<Vec2> rect = List.of(new Vec2(0, 0), new Vec2(100, 0), new Vec2(100, 50), new Vec2(0, 50));
        List<Vec2> out = Polylines.resample(rect, 7.3);
        assertEquals(rect.get(0), out.get(0));
        assertEquals(rect.get(rect.size() - 1), out.get(out.size() - 1));
        for (Vec2 p : out) {
            double d = Math.min(
                    Math.min(
                            Polylines.distanceToSegment(p, rect.get(0), rect.get(1)),
                            Polylines.distanceToSegment(p, rect.get(1), rect.get(2))),
                    Math.min(
                            Polylines.distanceToSegment(p, rect.get(2), rect.get(3)),
                            Polylines.distanceToSegment(p, rect.get(3), rect.get(0))));
            assertTrue(d < 1e-9, "resampled point " + p + " off the boundary by " + d);
        }
    }

    @Test
    void resampleValidatesArguments() {
        assertThrows(IllegalArgumentException.class, () -> Polylines.resample(List.of(new Vec2(0, 0)), 5.0));
        assertThrows(IllegalArgumentException.class,
                () -> Polylines.resample(List.of(new Vec2(0, 0), new Vec2(1, 1)), 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> Polylines.resample(List.of(new Vec2(0, 0), new Vec2(1, 1)), -1.0));
        assertThrows(IllegalArgumentException.class,
                () -> Polylines.resample(List.of(new Vec2(0, 0), new Vec2(1, 1)), Double.NaN));
    }

    @Test
    void distanceToSegmentProjectionInside() {
        assertEquals(1.0, Polylines.distanceToSegment(new Vec2(5, 1), new Vec2(0, 0), new Vec2(10, 0)), 1e-12);
        assertEquals(3.0, Polylines.distanceToSegment(new Vec2(5, -3), new Vec2(0, 0), new Vec2(10, 0)), 1e-12);
    }

    @Test
    void distanceToSegmentProjectsToEndpoints() {
        assertEquals(2.0, Polylines.distanceToSegment(new Vec2(-2, 0), new Vec2(0, 0), new Vec2(10, 0)), 1e-12);
        assertEquals(2.0, Polylines.distanceToSegment(new Vec2(12, 0), new Vec2(0, 0), new Vec2(10, 0)), 1e-12);
        assertEquals(StrictMath.sqrt(8.0),
                Polylines.distanceToSegment(new Vec2(-2, 2), new Vec2(0, 0), new Vec2(10, 0)), 1e-12);
    }

    @Test
    void distanceToSegmentDegenerateAndZero() {
        assertEquals(0.0, Polylines.distanceToSegment(new Vec2(3, 4), new Vec2(3, 4), new Vec2(3, 4)), 0.0);
        assertEquals(5.0, Polylines.distanceToSegment(new Vec2(3, 0), new Vec2(0, 4), new Vec2(0, 4)), 1e-12);
    }

    @Test
    void resampleAndChaikinAreDeterministic() {
        List<Vec2> pts = List.of(new Vec2(1, 2), new Vec2(7, -4), new Vec2(12, 9));
        assertEquals(Polylines.chaikin(pts, 2), Polylines.chaikin(pts, 2));
        assertEquals(Polylines.resample(pts, 3.3), Polylines.resample(pts, 3.3));
    }

    private static double dist(Vec2 a, Vec2 b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
