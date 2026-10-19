package ua.battlesim.math;

import java.util.Objects;

/**
 * A fixed-size {@code width x height} grid of floats stored row-major
 * (value at column {@code x}, row {@code y} lives at
 * {@code values[y * width + x]}), with nearest-neighbour and bilinear
 * sampling.
 *
 * <p>Sampling coordinates are in grid units: {@code x in [0, width)} and
 * {@code z in [0, height)}; samples outside the grid are clamped to the
 * nearest edge. All arithmetic is deterministic IEEE-754 — the same grid and
 * coordinates always give the same sample.
 *
 * <p>Mutable, intended for use on the single generation/simulation worker
 * thread (e.g. filled while a terrain is being generated); the returned
 * copies are immutable.
 */
public final class FloatGrid {

    private final int width;
    private final int height;
    private final float[] values;

    /**
     * @param width  number of columns, {@code >= 1}
     * @param height number of rows, {@code >= 1}
     * @throws IllegalArgumentException if a dimension is {@code < 1} or the
     *                                  grid does not fit into an int-sized array
     */
    public FloatGrid(int width, int height) {
        checkDimensions(width, height);
        this.width = width;
        this.height = height;
        this.values = new float[width * height];
    }

    /**
     * @param width  number of columns, {@code >= 1}
     * @param height number of rows, {@code >= 1}
     * @param values the initial values in row-major order, length exactly
     *               {@code width * height}
     * @throws IllegalArgumentException on invalid dimensions or a length
     *                                  mismatch
     */
    public FloatGrid(int width, int height, float[] values) {
        checkDimensions(width, height);
        Objects.requireNonNull(values, "values");
        if (values.length != width * height) {
            throw new IllegalArgumentException(
                    "values must have length " + (width * height) + " but has " + values.length);
        }
        this.width = width;
        this.height = height;
        this.values = values.clone();
    }

    /** @return the number of columns */
    public int width() {
        return width;
    }

    /** @return the number of rows */
    public int height() {
        return height;
    }

    /**
     * @param x column, in {@code [0, width)}
     * @param y row, in {@code [0, height)}
     * @return the stored value
     * @throws IllegalArgumentException if an index is out of range
     */
    public float get(int x, int y) {
        checkIndex(x, y);
        return values[y * width + x];
    }

    /**
     * @param x column, in {@code [0, width)}
     * @param y row, in {@code [0, height)}
     * @param v the value to store, finite
     * @throws IllegalArgumentException if an index is out of range or the value is not finite
     */
    public void set(int x, int y, float v) {
        checkIndex(x, y);
        if (!Float.isFinite(v)) {
            throw new IllegalArgumentException("value must be finite: " + v);
        }
        values[y * width + x] = v;
    }

    /**
     * @return a defensive copy of the stored values in row-major order
     */
    public float[] values() {
        return values.clone();
    }

    /**
     * Nearest-neighbour sampling: picks the cell whose column is the floor
     * of {@code x} (clamped to the grid).
     *
     * @param x sample column in grid units, finite
     * @param z sample row in grid units, finite
     * @return the nearest stored value
     * @throws IllegalArgumentException if a coordinate is not finite
     */
    public float nearest(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite: (" + x + ", " + z + ")");
        }
        int cx = clampIndex((int) StrictMath.floor(x), width);
        int cy = clampIndex((int) StrictMath.floor(z), height);
        return values[cy * width + cx];
    }

    /**
     * Bilinear sampling: interpolates between the four surrounding cells;
     * coordinates outside the grid are clamped to the nearest edge first.
     *
     * @param x sample column in grid units, finite
     * @param z sample row in grid units, finite
     * @return the bilinearly interpolated value
     * @throws IllegalArgumentException if a coordinate is not finite
     */
    public float bilinear(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite: (" + x + ", " + z + ")");
        }
        double cx = Smooth.clamp(x, 0.0, (double) width - 1.0);
        double cz = Smooth.clamp(z, 0.0, (double) height - 1.0);
        int x0 = (int) cx;
        if (x0 >= width - 1) {
            x0 = Math.max(0, width - 2);
        }
        int y0 = (int) cz;
        if (y0 >= height - 1) {
            y0 = Math.max(0, height - 2);
        }
        double fx = cx - x0;
        double fy = cz - y0;
        double v00 = values[y0 * width + x0];
        double v10 = width == 1 ? v00 : values[y0 * width + x0 + 1];
        double v01 = height == 1 ? v00 : values[(y0 + 1) * width + x0];
        double v11 = (width == 1 || height == 1) ? v00 : values[(y0 + 1) * width + x0 + 1];
        double top = width == 1 ? v00 : v00 + (v10 - v00) * fx;
        double bottom = height == 1 ? v00 : v01 + (v11 - v01) * fx;
        return (float) (height == 1 ? top : top + (bottom - top) * fy);
    }

    private static void checkDimensions(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("width and height must be >= 1: " + width + "x" + height);
        }
        if ((long) width * height > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("grid too large: " + width + "x" + height);
        }
    }

    private void checkIndex(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IllegalArgumentException("index (" + x + ", " + y + ") out of " + width + "x" + height);
        }
    }

    private static int clampIndex(int i, int bound) {
        return i < 0 ? 0 : i >= bound ? bound - 1 : i;
    }

    /** @return a string describing the grid (for logging), e.g. {@code "FloatGrid[4x4]"} */
    @Override
    public String toString() {
        return "FloatGrid[" + width + "x" + height + "]";
    }
}
