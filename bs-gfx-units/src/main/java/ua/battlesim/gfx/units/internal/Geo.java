package ua.battlesim.gfx.units.internal;

/**
 * Small procedural primitive geometry generators, producing interleaved
 * {@code pos(3) + normal(3)} triangle soup in local space.
 *
 * <p>Pure CPU, deterministic, NaN-free; unit-testable without a GPU.
 *
 * <p>All primitives are centred on the (x, y, z) they are given and sit in the
 * local unit frame ({@code +X} forward, {@code +Y} up, {@code +Z} right).
 */
public final class Geo {

    private Geo() {
    }

    /**
     * A box centred at {@code (cx, cy, cz)} with side lengths {@code (sx, sy, sz)}.
     *
     * @return 36 vertices (6 faces x 2 triangles)
     */
    public static float[] box(double cx, double cy, double cz, double sx, double sy, double sz) {
        float hx = (float) (sx / 2.0), hy = (float) (sy / 2.0), hz = (float) (sz / 2.0);
        float[][] c = new float[8][3];
        for (int i = 0; i < 8; i++) {
            c[i] = new float[] { (i & 1) == 1 ? hx : -hx, (i & 2) == 2 ? hy : -hy, (i & 4) == 4 ? hz : -hz };
        }
        int[] faces = {
                0, -1, 0, 2, 6, 4,
                0, 1, 1, 3, 7, 5,
                1, -1, 0, 1, 5, 4,
                1, 1, 2, 3, 7, 6,
                2, -1, 0, 1, 3, 2,
                2, 1, 4, 5, 7, 6
        };
        float[] out = new float[36 * Piece.STRIDE];
        int cur = 0;
        for (int f = 0; f < 6; f++) {
            int axis = faces[f * 6];
            int sign = faces[f * 6 + 1];
            float nx = (axis == 0) ? sign : 0, ny = (axis == 1) ? sign : 0, nz = (axis == 2) ? sign : 0;
            float[][] q = { c[faces[f * 6 + 2]], c[faces[f * 6 + 3]], c[faces[f * 6 + 4]], c[faces[f * 6 + 5]] };
            cur = tri(out, cur, (float)(cx + q[0][0]), (float)(cy + q[0][1]), (float)(cz + q[0][2]),
                    (float)(cx + q[1][0]), (float)(cy + q[1][1]), (float)(cz + q[1][2]),
                    (float)(cx + q[2][0]), (float)(cy + q[2][1]), (float)(cz + q[2][2]), nx, ny, nz);
            cur = tri(out, cur, (float)(cx + q[0][0]), (float)(cy + q[0][1]), (float)(cz + q[0][2]),
                    (float)(cx + q[2][0]), (float)(cy + q[2][1]), (float)(cz + q[2][2]),
                    (float)(cx + q[3][0]), (float)(cy + q[3][1]), (float)(cz + q[3][2]), nx, ny, nz);
        }
        return out;
    }

    /**
     * A cylinder with its axis along {@code Z}, centred at {@code (cx, cy, cz)},
     * radius {@code r} and half-length {@code halfLen}.
     *
     * @param n number of radial segments, {@code >= 3}
     * @return {@code 12*n} vertices (side + two caps)
     */
    public static float[] cylinderZ(double cx, double cy, double cz, double r, double halfLen, int n) {
        if (n < 3) {
            throw new IllegalArgumentException("n must be >= 3: " + n);
        }
        float[] out = new float[12 * n * Piece.STRIDE];
        float[][] ring = new float[n][3];
        float[][] normal = new float[n][3];
        for (int k = 0; k < n; k++) {
            double a = k * 2 * StrictMath.PI / n;
            ring[k] = new float[] { (float) (cx + r * StrictMath.cos(a)), (float) (cy + r * StrictMath.sin(a)), (float) (cz - halfLen) };
            normal[k] = new float[] { (float) StrictMath.cos(a), (float) StrictMath.sin(a), 0.0f };
        }
        int cur = 0;
        for (int k = 0; k < n; k++) {
            int k2 = (k + 1) % n;
            float[] p00 = ring[k];
            float[] p01 = { ring[k][0], ring[k][1], (float) (cz + halfLen) };
            float[] p10 = ring[k2];
            float[] p11 = { ring[k2][0], ring[k2][1], (float) (cz + halfLen) };
            cur = vert(out, cur, p00, normal[k]);
            cur = vert(out, cur, p01, normal[k]);
            cur = vert(out, cur, p11, normal[k2]);
            cur = vert(out, cur, p00, normal[k]);
            cur = vert(out, cur, p11, normal[k2]);
            cur = vert(out, cur, p10, normal[k2]);
        }
        float[] centerTop = { (float) cx, (float) cy, (float) (cz + halfLen) };
        for (int k = 0; k < n; k++) {
            int k2 = (k + 1) % n;
            cur = tri(out, cur, centerTop[0], centerTop[1], centerTop[2],
                    ring[k][0], ring[k][1], ring[k][2],
                    ring[k2][0], ring[k2][1], ring[k2][2], 0f, 0f, 1f);
        }
        float[] centerBot = { (float) cx, (float) cy, (float) (cz - halfLen) };
        for (int k = 0; k < n; k++) {
            int k2 = (k + 1) % n;
            cur = tri(out, cur, centerBot[0], centerBot[1], centerBot[2],
                    ring[k2][0], ring[k2][1], ring[k2][2],
                    ring[k][0], ring[k][1], ring[k][2], 0f, 0f, -1f);
        }
        return out;
    }

    /**
     * A flat disc (fan) in the {@code X-Z} plane at {@code y = 0}, centred at
     * {@code (cx, cz)} with radii {@code (rx, rz)}. Used for blob shadows.
     *
     * @param n number of radial segments, {@code >= 3}
     * @return {@code 3*n} vertices
     */
    public static float[] disc(double cx, double cz, double rx, double rz, int n) {
        if (n < 3) {
            throw new IllegalArgumentException("n must be >= 3: " + n);
        }
        float[] out = new float[3 * n * Piece.STRIDE];
        int cur = 0;
        float[] center = { (float) cx, 0.0f, (float) cz };
        for (int k = 0; k < n; k++) {
            double a0 = k * 2 * StrictMath.PI / n;
            double a1 = (k + 1) * 2 * StrictMath.PI / n;
            float[] p0 = { (float) (cx + rx * StrictMath.cos(a0)), 0.0f, (float) (cz + rz * StrictMath.sin(a0)) };
            float[] p1 = { (float) (cx + rx * StrictMath.cos(a1)), 0.0f, (float) (cz + rz * StrictMath.sin(a1)) };
            cur = tri(out, cur, center[0], center[1], center[2], p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], 0f, 1f, 0f);
        }
        return out;
    }

    /**
     * A flat ring (annulus) in the {@code X-Z} plane at {@code y = 0}, centred
     * at {@code (cx, cz)} between radii {@code r1} (inner) and {@code r2} (outer).
     * Used for the selection marker.
     *
     * @param n number of radial segments, {@code >= 3}
     * @return {@code 6*n} vertices
     */
    public static float[] ring(double cx, double cz, double r1, double r2, int n) {
        if (n < 3) {
            throw new IllegalArgumentException("n must be >= 3: " + n);
        }
        float[] out = new float[6 * n * Piece.STRIDE];
        int cur = 0;
        for (int k = 0; k < n; k++) {
            double a0 = k * 2 * StrictMath.PI / n;
            double a1 = (k + 1) * 2 * StrictMath.PI / n;
            float[] in0 = { (float) (cx + r1 * StrictMath.cos(a0)), 0.0f, (float) (cz + r1 * StrictMath.sin(a0)) };
            float[] in1 = { (float) (cx + r1 * StrictMath.cos(a1)), 0.0f, (float) (cz + r1 * StrictMath.sin(a1)) };
            float[] out0 = { (float) (cx + r2 * StrictMath.cos(a0)), 0.0f, (float) (cz + r2 * StrictMath.sin(a0)) };
            float[] out1 = { (float) (cx + r2 * StrictMath.cos(a1)), 0.0f, (float) (cz + r2 * StrictMath.sin(a1)) };
            cur = tri(out, cur, in0[0], in0[1], in0[2], out0[0], out0[1], out0[2], out1[0], out1[1], out1[2], 0f, 1f, 0f);
            cur = tri(out, cur, in0[0], in0[1], in0[2], out1[0], out1[1], out1[2], in1[0], in1[1], in1[2], 0f, 1f, 0f);
        }
        return out;
    }

    private static int tri(float[] out, int cur,
                           float x1, float y1, float z1,
                           float x2, float y2, float z2,
                           float x3, float y3, float z3,
                           float nx, float ny, float nz) {
        float[][] vv = { { x1, y1, z1 }, { x2, y2, z2 }, { x3, y3, z3 } };
        for (float[] p : vv) {
            out[cur++] = p[0];
            out[cur++] = p[1];
            out[cur++] = p[2];
            out[cur++] = nx;
            out[cur++] = ny;
            out[cur++] = nz;
        }
        return cur;
    }

    private static int vert(float[] out, int cur, float[] p, float[] n) {
        out[cur++] = p[0];
        out[cur++] = p[1];
        out[cur++] = p[2];
        out[cur++] = n[0];
        out[cur++] = n[1];
        out[cur++] = n[2];
        return cur;
    }
}