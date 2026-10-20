package ua.battlesim.gfx.world.internal;

import ua.battlesim.api.Road;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.Vec2;

import java.util.List;
import java.util.Objects;

/**
 * Builds the road ribbon meshes, draped on the terrain.
 *
 * <p>Each road polyline is turned into a ribbon. At every polyline point a
 * cross-section of {@value #ACROSS} vertices spans the road width (from edge
 * to edge). Between two consecutive points the {@value #ACROSS}-1 strips are
 * triangulated (2 triangles each). Every vertex is lifted by {@link #LIFT}
 * metres above the terrain so the ribbon hugs the surface.
 *
 * <p>Vertex count for a road with {@code P} points:
 * {@code (P - 1) * (ACROSS - 1) * 6}. A single-point road produces no geometry.
 *
 * <p>Per vertex: position (3) + up normal (3) — {@value #STRIDE} floats total.
 *
 * <p>Pure CPU geometry generation; unit-testable without a GPU.
 */
public final class RoadMeshBuilder {

    /** Floats per vertex: pos(3) + normal(3). */
    public static final int STRIDE = 6;

    /** Number of vertices across the road width. */
    public static final int ACROSS = 5;

    /** The ribbon lift above the terrain in metres. */
    public static final double LIFT = 0.15;

    private RoadMeshBuilder() {
    }

    /**
     * @param terrain the terrain (used for the draped height), non-null
     * @return the combined road ribbon mesh (possibly empty)
     */
    public static BuiltMesh build(Terrain terrain) {
        Objects.requireNonNull(terrain, "terrain");
        List<Road> roads = terrain.roads();
        Objects.requireNonNull(roads, "roads");
        int totalVertices = 0;
        for (Road road : roads) {
            Objects.requireNonNull(road, "road");
            totalVertices += roadVertexCount(road.points().size());
        }
        float[] out = new float[totalVertices * STRIDE];
        int cur = 0;
        for (Road road : roads) {
            cur = buildRoad(out, cur, terrain, road);
        }
        return new BuiltMesh(out, STRIDE);
    }

    /**
     * @param points the number of points in the road polyline
     * @return the number of vertices that polyline contributes
     */
    public static int roadVertexCount(int points) {
        if (points < 2) {
            return 0;
        }
        return (points - 1) * (ACROSS - 1) * 6;
    }

    private static int buildRoad(float[] out, int cur, Terrain terrain, Road road) {
        List<Vec2> pts = road.points();
        int n = pts.size();
        if (n < 2) {
            return cur;
        }
        double halfWidth = road.width() * 0.5;
        for (int k = 0; k < n - 1; k++) {
            Vec2 a = pts.get(k);
            Vec2 b = pts.get(k + 1);
            double dx = b.x() - a.x();
            double dz = b.z() - a.z();
            double len = StrictMath.hypot(dx, dz);
            if (len < 1.0e-9) {
                dx = 1.0;
                dz = 0.0;
                len = 1.0;
            }
            dx /= len;
            dz /= len;
            double px = -dz;
            double pz = dx;

            double[] ts = { -1.0, -0.5, 0.0, 0.5, 1.0 };
            double[] colA = new double[ACROSS * 2];
            double[] colB = new double[ACROSS * 2];
            for (int s = 0; s < ACROSS; s++) {
                double off = ts[s] * halfWidth;
                colA[s * 2] = a.x() + px * off;
                colA[s * 2 + 1] = a.z() + pz * off;
                colB[s * 2] = b.x() + px * off;
                colB[s * 2 + 1] = b.z() + pz * off;
            }
            for (int s = 0; s < ACROSS - 1; s++) {
                double ax = colA[s * 2], az = colA[s * 2 + 1];
                double bx = colA[(s + 1) * 2], bz = colA[(s + 1) * 2 + 1];
                double dxp = colB[s * 2], dzp = colB[s * 2 + 1];
                double cx = colB[(s + 1) * 2], cz = colB[(s + 1) * 2 + 1];
                cur = emit(out, cur, ax, terrain.heightAt(ax, az) + LIFT, az);
                cur = emit(out, cur, bx, terrain.heightAt(bx, bz) + LIFT, bz);
                cur = emit(out, cur, cx, terrain.heightAt(cx, cz) + LIFT, cz);
                cur = emit(out, cur, ax, terrain.heightAt(ax, az) + LIFT, az);
                cur = emit(out, cur, cx, terrain.heightAt(cx, cz) + LIFT, cz);
                cur = emit(out, cur, dxp, terrain.heightAt(dxp, dzp) + LIFT, dzp);
            }
        }
        return cur;
    }

    private static int emit(float[] out, int cur, double x, double y, double z) {
        out[cur++] = (float) x;
        out[cur++] = (float) y;
        out[cur++] = (float) z;
        out[cur++] = 0.0f;
        out[cur++] = 1.0f;
        out[cur++] = 0.0f;
        return cur;
    }
}