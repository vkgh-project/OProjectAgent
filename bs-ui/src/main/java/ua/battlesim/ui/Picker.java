package ua.battlesim.ui;

import ua.battlesim.api.*;
import ua.battlesim.gfx.core.OrbitCamera;
import ua.battlesim.ui.internal.LayoutMath;
import ua.battlesim.ui.internal.ScreenProjection;

import java.util.Objects;
import java.util.Optional;

/**
 * Picks a unit from screen coordinates using marker hit-test first,
 * then ray-versus-unit-spheres as fallback.
 *
 * <p>Picking order:
 * <ol>
 *   <li>Marker hit-test: check if (x, y) is within any NATO marker bounding box</li>
 *   <li>Ray-sphere: unproject to a ray, test intersection with each unit sphere,
 *       return the closest hit</li>
 * </ol>
 *
 * <p>Units are processed in ascending id order for determinism.
 *
 * <p>Thread-safe: all methods are static and stateless.
 */
public final class Picker {

    /** Default viewport width in pixels. */
    public static final int DEFAULT_VIEWPORT_WIDTH = 1920;

    /** Default viewport height in pixels. */
    public static final int DEFAULT_VIEWPORT_HEIGHT = 1080;

    private Picker() {}

    /**
     * Picks a unit at screen coordinates (x, y).
     *
     * <p>Strategy: marker hit-test first, then ray vs unit spheres.
     *
     * @param x screen x in pixels, finite
     * @param y screen y in pixels, finite
     * @param view the current session view (non-null)
     * @param camera the orbit camera (non-null)
     * @return the picked unit, or empty if no unit was hit
     * @throws IllegalArgumentException if x or y is not finite
     * @throws NullPointerException if view or camera is null
     */
    public static Optional<UnitView> pick(double x, double y, SessionView view, OrbitCamera camera) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(camera, "camera");
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("screen coords must be finite: " + x + ", " + y);
        }
        return pick(x, y, view, camera, DEFAULT_VIEWPORT_WIDTH, DEFAULT_VIEWPORT_HEIGHT);
    }

    /**
     * Picks a unit at screen coordinates with explicit viewport size.
     *
     * @param x screen x in pixels, finite
     * @param y screen y in pixels, finite
     * @param view the current session view (non-null)
     * @param camera the orbit camera (non-null)
     * @param viewportWidth viewport width in pixels, &gt; 0
     * @param viewportHeight viewport height in pixels, &gt; 0
     * @return the picked unit, or empty if no unit was hit
     * @throws IllegalArgumentException if x/y not finite or viewport not positive
     * @throws NullPointerException if view or camera is null
     */
    public static Optional<UnitView> pick(double x, double y, SessionView view, OrbitCamera camera,
                                          int viewportWidth, int viewportHeight) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(camera, "camera");
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("screen coords must be finite: " + x + ", " + y);
        }
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("viewport must be > 0: " + viewportWidth + ", " + viewportHeight);
        }

        var units = view.snapshot().units();

        // Pass 1: marker hit-test (process in ascending id order for determinism)
        var sorted = units.stream().sorted(java.util.Comparator.comparingInt(UnitView::id)).toList();
        for (UnitView u : sorted) {
            double[] screen = ScreenProjection.project(u.x(), 0.0, u.z(),
                    camera.viewProjectionMatrix(), viewportWidth, viewportHeight);
            Rect markerRect = LayoutMath.markerRect(screen[0], screen[1]);
            if (markerRect.contains(x, y)) {
                return Optional.of(u);
            }
        }

        // Pass 2: ray vs unit spheres
        ScreenProjection.Ray3 ray = ScreenProjection.unproject(
                x, y, camera.inverseViewProjectionMatrix(), viewportWidth, viewportHeight);

        UnitView best = null;
        double bestDist = Double.POSITIVE_INFINITY;
        for (UnitView u : sorted) {
            double radius = UnitInfo.of(u.type()).radius();
            double dist = ray.intersectSphere(u.x(), 0.0, u.z(), radius);
            if (dist > 0.0 && dist < bestDist) {
                bestDist = dist;
                best = u;
            }
        }
        return Optional.ofNullable(best);
    }
}
