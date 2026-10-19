package ua.battlesim.terrain.internal;

import ua.battlesim.api.Vec2;
import ua.battlesim.api.WaterField;
import ua.battlesim.math.FloatGrid;

/**
 * Internal {@link WaterField} backed by {@link FloatGrid} instances.
 *
 * <p>Thread-safety: the grids must be read-only after construction.
 */
public final class InternalWaterField implements WaterField {

    private final double cellSize;
    private final FloatGrid depth;
    private final FloatGrid flowX;
    private final FloatGrid flowZ;
    private final FloatGrid swamp;

    public InternalWaterField(double cellSize, FloatGrid depth, FloatGrid flowX,
            FloatGrid flowZ, FloatGrid swamp) {
        if (!Double.isFinite(cellSize) || cellSize <= 0.0) {
            throw new IllegalArgumentException("cellSize must be finite and > 0 (metres): " + cellSize);
        }
        this.cellSize = cellSize;
        this.depth = depth;
        this.flowX = flowX;
        this.flowZ = flowZ;
        this.swamp = swamp;
    }

    @Override
    public double depthAt(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite: (" + x + ", " + z + ")");
        }
        return depth.bilinear(x / cellSize, z / cellSize);
    }

    @Override
    public Vec2 flowAt(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite: (" + x + ", " + z + ")");
        }
        double gx = x / cellSize;
        double gz = z / cellSize;
        return new Vec2(flowX.bilinear(gx, gz), flowZ.bilinear(gx, gz));
    }

    @Override
    public double swampAt(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite: (" + x + ", " + z + ")");
        }
        return swamp.bilinear(x / cellSize, z / cellSize);
    }
}
