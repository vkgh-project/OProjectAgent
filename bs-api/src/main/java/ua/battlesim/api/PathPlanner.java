package ua.battlesim.api;

import java.util.List;
import java.util.Optional;

/**
 * Terrain-aware path planning on top of {@link NavGrid}.
 *
 * <p>Units: positions in metres (x east, z south); radius in metres.
 *
 * <p>Deterministic: the same inputs always yield the same route.
 */
public interface PathPlanner {

    /**
     * Plans a path for a unit of the given type and radius in metres from
     * {@code from} to {@code to}, both in metres.
     *
     * @return the ordered waypoints (from {@code from} to {@code to}) or
     *         empty when no path exists
     */
    Optional<List<Vec2>> plan(Vec2 from, Vec2 to, UnitType type, double radius);
}
