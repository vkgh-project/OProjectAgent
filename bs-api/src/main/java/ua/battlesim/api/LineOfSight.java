package ua.battlesim.api;

/**
 * Terrain-aware line-of-sight queries.
 *
 * <p>Units: positions in metres (x east, z south); eye heights in metres
 * above the local ground.
 *
 * <p>Deterministic and read-only; safe to call from the simulation worker
 * thread.
 */
public interface LineOfSight {

    /**
     * @param from    the observer position in metres
     * @param eyeFrom the observer eye height in metres above the local ground
     * @param to      the target position in metres
     * @param eyeTo   the target eye height in metres above the local ground
     * @return true if the straight sight line is not blocked by terrain or
     *         props
     */
    boolean visible(Vec2 from, double eyeFrom, Vec2 to, double eyeTo);
}
