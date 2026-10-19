package ua.battlesim.terrain.internal;

import ua.battlesim.api.HeightField;
import ua.battlesim.math.FloatGrid;

/**
 * Internal {@link HeightField} backed by a {@link FloatGrid}.
 *
 * <p>Thread-safety: the grid must be read-only after construction.
 */
public final class InternalHeightField implements HeightField {

    private final double size;
    private final double cellSize;
    private final FloatGrid grid;

    public InternalHeightField(double size, double cellSize, FloatGrid grid) {
        if (!Double.isFinite(size) || size <= 0.0) {
            throw new IllegalArgumentException("size must be finite and > 0 (metres): " + size);
        }
        if (!Double.isFinite(cellSize) || cellSize <= 0.0) {
            throw new IllegalArgumentException("cellSize must be finite and > 0 (metres): " + cellSize);
        }
        this.size = size;
        this.cellSize = cellSize;
        this.grid = grid;
    }

    @Override
    public double size() {
        return size;
    }

    @Override
    public double heightAt(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite: (" + x + ", " + z + ")");
        }
        return grid.bilinear(x / cellSize, z / cellSize);
    }
}
