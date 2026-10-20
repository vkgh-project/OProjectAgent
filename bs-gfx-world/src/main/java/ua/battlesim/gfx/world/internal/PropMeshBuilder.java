package ua.battlesim.gfx.world.internal;

import ua.battlesim.api.Prop;
import ua.battlesim.api.Terrain;

import java.util.List;
import java.util.Objects;

/**
 * Builds the instanced tree and rock meshes.
 *
 * <p>Each prop type has a small fixed base geometry (a low-poly tree or an
 * octahedral rock) shared by all instances, plus per-instance data
 * ({@code x, y, z, scale, rotation, tint, kind} — {@value #INSTANCE_STRIDE}
 * floats). The base geometry vertex counts are fixed:
 * {@value #TREE_BASE_VERTEX_COUNT} for trees and {@value #ROCK_BASE_VERTEX_COUNT}
 * for rocks, so the output is exactly countable and NaN-free.
 *
 * <p>Each instance is placed at its {@link Prop} position, sitting on the
 * terrain (its y is the ground height there). The per-instance data drives the
 * vertex-shader wind sway (trees), distance culling and tint.
 *
 * <p>Pure CPU geometry generation; unit-testable without a GPU.
 */
public final class PropMeshBuilder {

    /** Floats per base vertex: pos(3) + normal(3). */
    public static final int BASE_STRIDE = 6;

    /** Number of floats per instance: x,y,z,scale,rotation,tint,kind. */
    public static final int INSTANCE_STRIDE = 7;

    /** The shader location of the per-instance position attribute (vec3). */
    public static final int INSTANCE_LOCATION = 2;

    /** The shader location of the per-instance params attribute (vec4). */
    public static final int INSTANCE_PARAMS_LOCATION = 3;

    /** Base geometry vertex count for a tree (trunk 24 + foliage 48). */
    public static final int TREE_BASE_VERTEX_COUNT = 72;

    /** Base geometry vertex count for a rock (octahedron, 8 faces). */
    public static final int ROCK_BASE_VERTEX_COUNT = 24;

    private PropMeshBuilder() {
    }

    /**
     * A built prop set: the shared base geometry and the per-instance data.
     *
     * @param baseVertices  interleaved base-geometry floats
     * @param baseStride    floats per base vertex
     * @param instanceData  per-instance floats (empty when there are no props)
     * @param instanceCount the number of instances
     */
    public record PropSet(float[] baseVertices, int baseStride, float[] instanceData, int instanceCount) {
        public PropSet {
            Objects.requireNonNull(baseVertices, "baseVertices");
            if (baseStride <= 0) {
                throw new IllegalArgumentException("baseStride must be > 0: " + baseStride);
            }
            if (baseVertices.length % baseStride != 0) {
                throw new IllegalArgumentException("baseVertices not a multiple of baseStride");
            }
            Objects.requireNonNull(instanceData, "instanceData");
            if (instanceCount < 0) {
                throw new IllegalArgumentException("instanceCount must be >= 0: " + instanceCount);
            }
            if (instanceData.length != instanceCount * PropMeshBuilder.INSTANCE_STRIDE) {
                throw new IllegalArgumentException("instanceData length mismatch");
            }
        }

        /** @return the number of base vertices */
        public int baseVertexCount() {
            return baseVertices.length / baseStride;
        }

        /** @return true if every float (base + instances) is finite */
        public boolean isFinite() {
            for (float v : baseVertices) {
                if (!Float.isFinite(v)) {
                    return false;
                }
            }
            for (float v : instanceData) {
                if (!Float.isFinite(v)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * @param terrain the terrain (trees + ground height), non-null
     * @return the tree base geometry and per-instance data
     */
    public static PropSet buildTrees(Terrain terrain) {
        Objects.requireNonNull(terrain, "terrain");
        List<Prop> trees = terrain.trees();
        return new PropSet(buildTreeBase(), BASE_STRIDE, pack(terrain, trees), trees.size());
    }

    /**
     * @param terrain the terrain (rocks + ground height), non-null
     * @return the rock base geometry and per-instance data
     */
    public static PropSet buildRocks(Terrain terrain) {
        Objects.requireNonNull(terrain, "terrain");
        List<Prop> rocks = terrain.rocks();
        return new PropSet(buildRockBase(), BASE_STRIDE, pack(terrain, rocks), rocks.size());
    }

    private static float[] pack(Terrain terrain, List<Prop> props) {
        Objects.requireNonNull(props, "props");
        float[] data = new float[props.size() * INSTANCE_STRIDE];
        int cur = 0;
        for (Prop p : props) {
            Objects.requireNonNull(p, "prop");
            double ground = terrain.heightAt(p.x(), p.z());
            data[cur++] = (float) p.x();
            data[cur++] = (float) ground;
            data[cur++] = (float) p.z();
            data[cur++] = (float) p.scale();
            data[cur++] = (float) p.rotation();
            data[cur++] = (float) p.tint();
            data[cur++] = (float) p.kind();
        }
        return data;
    }

    /** Builds the shared low-poly tree base geometry (72 vertices). */
    private static float[] buildTreeBase() {
        float[] out = new float[TREE_BASE_VERTEX_COUNT * BASE_STRIDE];
        int cur = 0;
        float[][] bottom = new float[4][3];
        float[][] top = new float[4][3];
        for (int k = 0; k < 4; k++) {
            double a = k * StrictMath.PI / 2.0;
            bottom[k][0] = (float) (0.12 * StrictMath.cos(a));
            bottom[k][1] = 0.0f;
            bottom[k][2] = (float) (0.12 * StrictMath.sin(a));
            top[k][0] = (float) (0.08 * StrictMath.cos(a));
            top[k][1] = 0.5f;
            top[k][2] = (float) (0.08 * StrictMath.sin(a));
        }
        for (int k = 0; k < 4; k++) {
            int k2 = (k + 1) % 4;
            cur = emitTriangle(out, cur, bottom[k], bottom[k2], top[k2]);
            cur = emitTriangle(out, cur, bottom[k], top[k2], top[k]);
        }
        float[][] ring = new float[8][3];
        for (int k = 0; k < 8; k++) {
            double a = k * StrictMath.PI / 4.0;
            ring[k][0] = (float) (0.6 * StrictMath.cos(a));
            ring[k][1] = 0.4f;
            ring[k][2] = (float) (0.6 * StrictMath.sin(a));
        }
        float[] apex = { 0.0f, 1.6f, 0.0f };
        float[] center = { 0.0f, 0.4f, 0.0f };
        for (int k = 0; k < 8; k++) {
            int k2 = (k + 1) % 8;
            cur = emitTriangle(out, cur, apex, ring[k], ring[k2]);
            cur = emitTriangle(out, cur, center, ring[k2], ring[k]);
        }
        return out;
    }

    /** Builds the shared octahedral rock base geometry (24 vertices). */
    private static float[] buildRockBase() {
        float[] out = new float[ROCK_BASE_VERTEX_COUNT * BASE_STRIDE];
        int cur = 0;
        float[] top = { 0.0f, 0.5f, 0.0f };
        float[] bot = { 0.0f, -0.5f, 0.0f };
        float[][] eq = new float[4][3];
        for (int k = 0; k < 4; k++) {
            double a = k * StrictMath.PI / 2.0;
            eq[k][0] = (float) (0.6 * StrictMath.cos(a));
            eq[k][1] = 0.0f;
            eq[k][2] = (float) (0.6 * StrictMath.sin(a));
        }
        for (int k = 0; k < 4; k++) {
            int k2 = (k + 1) % 4;
            cur = emitTriangle(out, cur, top, eq[k], eq[k2]);
            cur = emitTriangle(out, cur, bot, eq[k2], eq[k]);
        }
        return out;
    }

    private static int emitTriangle(float[] out, int cur, float[] a, float[] b, float[] c) {
        float[] n = faceNormal(a, b, c);
        float[][] verts = { a, b, c };
        for (float[] v : verts) {
            out[cur++] = v[0];
            out[cur++] = v[1];
            out[cur++] = v[2];
            out[cur++] = n[0];
            out[cur++] = n[1];
            out[cur++] = n[2];
        }
        return cur;
    }

    private static float[] faceNormal(float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float len = (float) StrictMath.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1.0e-12) {
            return new float[] { 0.0f, 1.0f, 0.0f };
        }
        return new float[] { nx / len, ny / len, nz / len };
    }
}