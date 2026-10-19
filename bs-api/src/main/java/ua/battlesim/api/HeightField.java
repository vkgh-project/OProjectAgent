package ua.battlesim.api;

/**
 * Elevation of the terrain surface.
 *
 * <p>Units: metres throughout (x, z, size, elevation); x east, z south.
 *
 * <p>Determinism: {@link #heightAt} must return the same value for the same
 * coordinates; no wall clock, no unseeded randomness.
 *
 * <p>Thread-safety: the render thread only reads values from snapshots;
 * implementations are queried from the simulation worker thread and must be
 * immutable after generation.
 */
public interface HeightField {

    /**
     * @return the side length of the square map in metres; the map spans
     *         {@code [0, size] x [0, size]}
     */
    double size();

    /**
     * @param x east-west position in metres
     * @param z north-south position in metres (south positive)
     * @return the ground elevation in metres
     */
    double heightAt(double x, double z);
}
