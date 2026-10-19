package ua.battlesim.api;

import java.util.List;
import java.util.Objects;

import static java.lang.Double.isFinite;

/**
 * A road polyline with its bridge segments marked.
 *
 * <p>Units: points in metres (x east, z south); width in metres.
 * {@code onBridge[k]} marks whether the segment from point k-1 to point k
 * (k = 0: the first point) crosses water on a bridge; its length must equal
 * the number of points.
 *
 * <p>Immutable and thread-safe; points and flags are copied defensively.
 */
public record Road(List<Vec2> points, double width, int kind, boolean[] onBridge) {

    public Road {
        Objects.requireNonNull(points, "points");
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Road must have at least one point");
        }
        for (Vec2 p : points) {
            Objects.requireNonNull(p, "road point");
        }
        points = List.copyOf(points);
        if (!isFinite(width) || width <= 0.0) {
            throw new IllegalArgumentException("width must be finite and > 0 (metres): " + width);
        }
        if (kind < 0) {
            throw new IllegalArgumentException("kind must be >= 0: " + kind);
        }
        Objects.requireNonNull(onBridge, "onBridge");
        if (onBridge.length != points.size()) {
            throw new IllegalArgumentException(
                    "onBridge length " + onBridge.length + " must equal points size " + points.size());
        }
        onBridge = onBridge.clone();
    }
}
