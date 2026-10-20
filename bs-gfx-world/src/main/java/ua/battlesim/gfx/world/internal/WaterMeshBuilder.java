package ua.battlesim.gfx.world.internal;

import ua.battlesim.api.Terrain;
import ua.battlesim.api.Vec2;
import ua.battlesim.api.WaterField;

import java.util.Objects;

/**
 * Builds the water surface mesh.
 *
 * <p>The map is sampled on the same uniform {@code cells x cells} grid used
 * for the terrain. A grid cell is emitted (as two triangles, 6 vertices) only
 * if at least one of its four corners has {@code depthAt > 0}, so dry land
 * produces no water geometry. The water surface is a flat plane at
 * {@link #WATER_LEVEL}.
 *
 * <p>Per vertex: position (3) and the flow + depth attribute (3: flowX,
 * flowZ, depth) — {@value #STRIDE} floats total. The shader derives the
 * animated normals, foam and the soft shoreline fade from these.
 *
 * <p>Pure CPU geometry generation; unit-testable without a GPU.
 */
public final class WaterMeshBuilder {

    /** Floats per vertex: pos(3) + flowX, flowZ, depth(3). */
    public static final int STRIDE = 6;

    /** The flat water surface elevation in metres. */
    public static final double WATER_LEVEL = 0.0;

    private WaterMeshBuilder() {
    }

    /**
     * @param terrain the terrain (its water field is used), non-null
     * @param cells   the number of grid cells per side, {@code >= 1}
     * @return the built water surface mesh (possibly empty)
     */
    public static BuiltMesh build(Terrain terrain, int cells) {
        Objects.requireNonNull(terrain, "terrain");
        if (cells < 1) {
            throw new IllegalArgumentException("cells must be >= 1: " + cells);
        }
        double size = terrain.size();
        if (!Double.isFinite(size) || size <= 0.0) {
            throw new IllegalArgumentException("terrain size must be finite and > 0: " + size);
        }
        double step = size / cells;
        int grid = cells + 1;
        WaterField water = terrain.water();

        double[] depth = new double[grid * grid];
        double[] flowX = new double[grid * grid];
        double[] flowZ = new double[grid * grid];
        for (int j = 0; j < grid; j++) {
            for (int i = 0; i < grid; i++) {
                int idx = i + j * grid;
                double x = i * step;
                double z = j * step;
                depth[idx] = Math.max(0.0, water.depthAt(x, z));
                Vec2 flow = water.flowAt(x, z);
                flowX[idx] = flow.x();
                flowZ[idx] = flow.z();
            }
        }

        int wetCells = wetCellCount(depth, cells, grid);
        float[] out = new float[wetCells * 6 * STRIDE];
        int cur = 0;
        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                int c00 = i + j * grid;
                int c10 = (i + 1) + j * grid;
                int c11 = (i + 1) + (j + 1) * grid;
                int c01 = i + (j + 1) * grid;
                if (!(depth[c00] > 0.0 || depth[c10] > 0.0 || depth[c11] > 0.0 || depth[c01] > 0.0)) {
                    continue;
                }
                double avgDepth = (depth[c00] + depth[c10] + depth[c11] + depth[c01]) * 0.25;
                double fx = (flowX[c00] + flowX[c10] + flowX[c11] + flowX[c01]) * 0.25;
                double fz = (flowZ[c00] + flowZ[c10] + flowZ[c11] + flowZ[c01]) * 0.25;
                double x0 = i * step, z0 = j * step;
                double x1 = (i + 1) * step, z1 = (j + 1) * step;
                cur = emit(out, cur, x0, WATER_LEVEL, z0, fx, fz, avgDepth);
                cur = emit(out, cur, x1, WATER_LEVEL, z0, fx, fz, avgDepth);
                cur = emit(out, cur, x1, WATER_LEVEL, z1, fx, fz, avgDepth);
                cur = emit(out, cur, x0, WATER_LEVEL, z0, fx, fz, avgDepth);
                cur = emit(out, cur, x1, WATER_LEVEL, z1, fx, fz, avgDepth);
                cur = emit(out, cur, x0, WATER_LEVEL, z1, fx, fz, avgDepth);
            }
        }
        return new BuiltMesh(out, STRIDE);
    }

    /**
     * @param terrain the terrain (its water field is used), non-null
     * @param cells   the number of grid cells per side, {@code >= 1}
     * @return the number of grid cells that contain water
     */
    public static int wetCellCount(Terrain terrain, int cells) {
        Objects.requireNonNull(terrain, "terrain");
        if (cells < 1) {
            throw new IllegalArgumentException("cells must be >= 1: " + cells);
        }
        double size = terrain.size();
        double step = size / cells;
        int grid = cells + 1;
        WaterField water = terrain.water();
        double[] depth = new double[grid * grid];
        for (int j = 0; j < grid; j++) {
            for (int i = 0; i < grid; i++) {
                depth[i + j * grid] = Math.max(0.0, water.depthAt(i * step, j * step));
            }
        }
        return wetCellCount(depth, cells, grid);
    }

    private static int wetCellCount(double[] depth, int cells, int grid) {
        int count = 0;
        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                int c00 = i + j * grid;
                int c10 = (i + 1) + j * grid;
                int c11 = (i + 1) + (j + 1) * grid;
                int c01 = i + (j + 1) * grid;
                if (depth[c00] > 0.0 || depth[c10] > 0.0 || depth[c11] > 0.0 || depth[c01] > 0.0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int emit(float[] out, int cur, double x, double y, double z,
                            double fx, double fz, double depth) {
        out[cur++] = (float) x;
        out[cur++] = (float) y;
        out[cur++] = (float) z;
        out[cur++] = (float) fx;
        out[cur++] = (float) fz;
        out[cur++] = (float) depth;
        return cur;
    }
}