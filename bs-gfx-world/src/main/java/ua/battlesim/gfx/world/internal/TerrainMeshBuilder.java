package ua.battlesim.gfx.world.internal;

import ua.battlesim.api.Prop;
import ua.battlesim.api.Terrain;

import java.util.List;
import java.util.Objects;

/**
 * Builds the terrain surface mesh from the {@link Terrain} height field.
 *
 * <p>The map is sampled on a uniform {@code cells x cells} grid; each grid
 * cell contributes two triangles (6 vertices, triangle-soup). Per vertex the
 * builder stores: position (3), normal (3), and the baked attributes
 * swamp, forest and shade (3) — {@value #STRIDE} floats total.
 *
 * <p>Normals are computed with central finite differences of the height field
 * (one-sided at the border), always finite and pointing up. The {@code forest}
 * attribute is a deterministic function of the nearby tree props, and the
 * {@code swamp}/{@code shade} attributes come straight from the terrain.
 *
 * <p>Pure CPU geometry generation: no OpenGL, so it is unit-testable without
 * a GPU.
 */
public final class TerrainMeshBuilder {

    /** Floats per vertex: pos(3) + normal(3) + swamp, forest, shade (3). */
    public static final int STRIDE = 9;

    private TerrainMeshBuilder() {
    }

    /**
     * @param terrain the terrain to sample (non-null)
     * @param cells   the number of grid cells per side, {@code >= 1}
     * @return the built terrain surface mesh
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
        int cornerCount = grid * grid;

        double[] h = new double[cornerCount];
        for (int j = 0; j < grid; j++) {
            for (int i = 0; i < grid; i++) {
                h[i + j * grid] = terrain.heightAt(i * step, j * step);
            }
        }

        double[] nx = new double[cornerCount];
        double[] ny = new double[cornerCount];
        double[] nz = new double[cornerCount];
        double[] swamp = new double[cornerCount];
        double[] forest = new double[cornerCount];
        double[] shade = new double[cornerCount];

        List<Prop> trees = terrain.trees();
        double forestRadius = step;
        for (int j = 0; j < grid; j++) {
            for (int i = 0; i < grid; i++) {
                int idx = i + j * grid;
                double x = i * step;
                double z = j * step;

                int i0 = Math.max(0, i - 1);
                int i1 = Math.min(cells, i + 1);
                int j0 = Math.max(0, j - 1);
                int j1 = Math.min(cells, j + 1);
                double dx = i1 > i0
                        ? (h[i1 + j * grid] - h[i0 + j * grid]) / ((i1 - i0) * step) : 0.0;
                double dz = j1 > j0
                        ? (h[i + j1 * grid] - h[i + j0 * grid]) / ((j1 - j0) * step) : 0.0;
                double[] n = normalize(-dx, 1.0, -dz);
                nx[idx] = n[0];
                ny[idx] = n[1];
                nz[idx] = n[2];

                swamp[idx] = clamp01(terrain.water().swampAt(x, z));
                shade[idx] = clamp01(terrain.shadeAt(x, z));
                forest[idx] = forestDensity(x, z, trees, forestRadius);
            }
        }

        int vertexCount = cells * cells * 6;
        float[] out = new float[vertexCount * STRIDE];
        int cur = 0;
        for (int j = 0; j < cells; j++) {
            for (int i = 0; i < cells; i++) {
                int c00 = i + j * grid;
                int c10 = (i + 1) + j * grid;
                int c11 = (i + 1) + (j + 1) * grid;
                int c01 = i + (j + 1) * grid;
                cur = emitCorner(out, cur, c00, i, j, h, nx, ny, nz, swamp, forest, shade, step);
                cur = emitCorner(out, cur, c10, i + 1, j, h, nx, ny, nz, swamp, forest, shade, step);
                cur = emitCorner(out, cur, c11, i + 1, j + 1, h, nx, ny, nz, swamp, forest, shade, step);
                cur = emitCorner(out, cur, c00, i, j, h, nx, ny, nz, swamp, forest, shade, step);
                cur = emitCorner(out, cur, c11, i + 1, j + 1, h, nx, ny, nz, swamp, forest, shade, step);
                cur = emitCorner(out, cur, c01, i, j + 1, h, nx, ny, nz, swamp, forest, shade, step);
            }
        }
        return new BuiltMesh(out, STRIDE);
    }

    private static int emitCorner(float[] out, int cur, int idx, int gi, int gj,
                                  double[] h, double[] nx, double[] ny, double[] nz,
                                  double[] swamp, double[] forest, double[] shade, double step) {
        out[cur++] = (float) (gi * step);
        out[cur++] = (float) h[idx];
        out[cur++] = (float) (gj * step);
        out[cur++] = (float) nx[idx];
        out[cur++] = (float) ny[idx];
        out[cur++] = (float) nz[idx];
        out[cur++] = (float) swamp[idx];
        out[cur++] = (float) forest[idx];
        out[cur++] = (float) shade[idx];
        return cur;
    }

    private static double[] normalize(double x, double y, double z) {
        double len = StrictMath.sqrt(x * x + y * y + z * z);
        if (len < 1.0e-12) {
            return new double[] { 0.0, 1.0, 0.0 };
        }
        return new double[] { x / len, y / len, z / len };
    }

    private static double forestDensity(double x, double z, List<Prop> trees, double radius) {
        if (trees == null) {
            return 0.0;
        }
        int count = 0;
        double r2 = radius * radius;
        for (Prop p : trees) {
            double dx = p.x() - x;
            double dz = p.z() - z;
            if (dx * dx + dz * dz <= r2) {
                count++;
            }
        }
        return Math.min(1.0, count * 0.5);
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, v));
    }
}