package ua.battlesim.math;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import ua.battlesim.api.Vec2;

/**
 * Stateless polyline utilities (the {@code Polylines} group of the bs-math
 * public API). Points are {@link Vec2} in metres (x east, z south).
 *
 * <p>All methods are pure functions of their inputs (no static mutable
 * state, no randomness), so results are deterministic and thread-safe.
 */
public final class Polylines {

    private Polylines() {
        throw new AssertionError("Polylines is a static utility class");
    }

    /**
     * Chaikin's corner-cutting subdivision: each interior edge (p[i-1], p[i])
     * is replaced by the points at 1/4 and 3/4 of the edge. The first and
     * last points of the polyline are preserved exactly.
     *
     * <p>For {@code n} input points and {@code k} iterations the result has
     * {@code 2 + (n - 2) * 2^k} points (with {@code n >= 2}).
     *
     * @param points     the input polyline, at least 2 finite points, non-null elements
     * @param iterations number of subdivisions, {@code >= 0}
     * @return the subdivided polyline, a new immutable list
     * @throws IllegalArgumentException on an invalid argument
     */
    public static List<Vec2> chaikin(List<Vec2> points, int iterations) {
        Objects.requireNonNull(points, "points");
        if (points.size() < 2) {
            throw new IllegalArgumentException("points must contain at least 2 points: " + points.size());
        }
        if (iterations < 0) {
            throw new IllegalArgumentException("iterations must be >= 0: " + iterations);
        }
        for (Vec2 p : points) {
            Objects.requireNonNull(p, "point");
        }
        List<Vec2> out = new ArrayList<>(points);
        for (int it = 0; it < iterations; it++) {
            List<Vec2> next = new ArrayList<>(out.size() * 2 - 2);
            next.add(out.get(0));
            for (int i = 1; i < out.size() - 1; i++) {
                Vec2 a = out.get(i - 1);
                Vec2 b = out.get(i);
                next.add(new Vec2(a.x() + (b.x() - a.x()) * 0.25, a.z() + (b.z() - a.z()) * 0.25));
                next.add(new Vec2(a.x() + (b.x() - a.x()) * 0.75, a.z() + (b.z() - a.z()) * 0.75));
            }
            next.add(out.get(out.size() - 1));
            out = next;
        }
        return List.copyOf(out);
    }

    /**
     * Resamples a polyline into points equally spaced by {@code step} metres
     * along its path.
     *
     * <p>The result always starts at the first input point and ends at the
     * last input point. Consecutive interior points are spaced at
     * {@code step} (within floating-point error); the final gap to the
     * endpoint is whatever remains of the last partial segment.
     *
     * @param points the input polyline, at least 2 finite points, non-null elements
     * @param step   the desired spacing in metres, finite, {@code > 0}
     * @return the resampled polyline, a new immutable list
     * @throws IllegalArgumentException on an invalid argument
     */
    public static List<Vec2> resample(List<Vec2> points, double step) {
        Objects.requireNonNull(points, "points");
        if (points.size() < 2) {
            throw new IllegalArgumentException("points must contain at least 2 points: " + points.size());
        }
        if (!Double.isFinite(step) || step <= 0.0) {
            throw new IllegalArgumentException("step must be finite and > 0 (metres): " + step);
        }
        for (Vec2 p : points) {
            Objects.requireNonNull(p, "point");
        }
        List<Vec2> out = new ArrayList<>();
        out.add(points.get(0));
        double travelled = 0.0;
        double nextAt = step;
        Vec2 a = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            Vec2 b = points.get(i);
            double segStart = travelled;
            double segLen = distance(a, b);
            double segEnd = segStart + segLen;
            while (segEnd >= nextAt) {
                // invariant at segment entry: nextAt > segStart; if segLen == 0
                // the loop body is never reached, so no division by zero
                double t = (nextAt - segStart) / segLen;
                out.add(new Vec2(a.x() + (b.x() - a.x()) * t, a.z() + (b.z() - a.z()) * t));
                nextAt += step;
            }
            travelled = segEnd;
            a = b;
        }
        Vec2 last = points.get(points.size() - 1);
        Vec2 tail = out.get(out.size() - 1);
        if (tail.x() != last.x() || tail.z() != last.z()) {
            out.add(last);
        }
        return List.copyOf(out);
    }

    /**
     * Euclidean distance from a point to a segment.
     *
     * @param p the point (metres)
     * @param a the segment start (metres)
     * @param b the segment end (metres)
     * @return the distance in metres, {@code >= 0}
     */
    public static double distanceToSegment(Vec2 p, Vec2 a, Vec2 b) {
        Objects.requireNonNull(p, "p");
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        double abx = b.x() - a.x();
        double abz = b.z() - a.z();
        double len2 = abx * abx + abz * abz;
        if (len2 == 0.0) {
            return distance(p, a);
        }
        double t = ((p.x() - a.x()) * abx + (p.z() - a.z()) * abz) / len2;
        if (t < 0.0) {
            t = 0.0;
        } else if (t > 1.0) {
            t = 1.0;
        }
        return distance(p, new Vec2(a.x() + abx * t, a.z() + abz * t));
    }

    private static double distance(Vec2 p, Vec2 q) {
        double dx = p.x() - q.x();
        double dz = p.z() - q.z();
        return StrictMath.sqrt(dx * dx + dz * dz);
    }
}
