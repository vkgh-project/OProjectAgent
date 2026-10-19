package ua.battlesim.api;

import java.util.List;

/**
 * The complete deterministic world a round is simulated in: heights, water,
 * navigation, line of sight, path planning, roads, bridges, static props and
 * lighting information for rendering.
 *
 * <p>Units: metres, seconds and radians throughout; x east, z south.
 *
 * <p>Thread-safety: implementations must be immutable after generation; the
 * render thread only reads the view methods (roads, bridges, props,
 * sunDirection, shadeAt) and the simulation worker thread calls
 * {@link #nav()}, {@link #los()} and {@link #planner()}.
 */
public interface Terrain extends HeightField {

    /** @return water depth, current and swamp over the map */
    WaterField water();

    /** @return the navigation grid over the map */
    NavGrid nav();

    /** @return line-of-sight queries */
    LineOfSight los();

    /** @return terrain-aware path planning */
    PathPlanner planner();

    /** @return roads on the map (immutable list, deterministic order) */
    List<Road> roads();

    /** @return bridges crossing water (immutable list, deterministic order) */
    List<Bridge> bridges();

    /** @return placed trees (immutable list, deterministic order) */
    List<Prop> trees();

    /** @return placed rocks (immutable list, deterministic order) */
    List<Prop> rocks();

    /**
     * @return the unit direction of the sun in the 3-D world
     *         (x east, y up, z south)
     */
    double[] sunDirection();

    /**
     * @param x position in metres
     * @param z position in metres (south positive)
     * @return the terrain shading at (x, z) in {@code [0, 1]} (1 = full sun)
     */
    double shadeAt(double x, double z);
}
