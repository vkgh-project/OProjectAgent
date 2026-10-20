package ua.battlesim.gfx.world.internal;

import ua.battlesim.api.Bridge;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.Vec2;

import java.util.List;
import java.util.Objects;

/**
 * Builds the bridge meshes: a deck, two side rails and three pillars per
 * bridge.
 *
 * <p>Every element is an axis-aligned (in the bridge's local frame) box with
 * 6 faces = 12 triangles = {@value #VERTS_PER_BOX} vertices. A bridge has
 * {@value #BOXES_PER_BRIDGE} boxes (1 deck + 2 rails + 3 pillars), so
 * {@value #VERTS_PER_BRIDGE} vertices in total.
 *
 * <p>The deck is a flat slab at {@code deckHeight} above the water level,
 * spanning start to end; the rails run along both edges; the pillars drop from
 * the water surface up to the deck at both ends and the midpoint. A
 * degenerate (zero-length) bridge falls back to a fixed direction so the
 * output stays finite.
 *
 * <p>Per vertex: position (3) + normal (3) — {@value #STRIDE} floats total.
 *
 * <p>Pure CPU geometry generation; unit-testable without a GPU.
 */
public final class BridgeMeshBuilder {

    /** Floats per vertex: pos(3) + normal(3). */
    public static final int STRIDE = 6;

    /** Vertices per box (6 faces x 2 triangles x 3). */
    public static final int VERTS_PER_BOX = 36;

    /** Boxes per bridge: deck(1) + rails(2) + pillars(3). */
    public static final int BOXES_PER_BRIDGE = 6;

    /** Total vertices per bridge. */
    public static final int VERTS_PER_BRIDGE = VERTS_PER_BOX * BOXES_PER_BRIDGE;

    private BridgeMeshBuilder() {
    }

    /**
     * @param terrain the terrain (non-null; bridges come from it)
     * @return the combined bridge mesh
     */
    public static BuiltMesh build(Terrain terrain) {
        Objects.requireNonNull(terrain, "terrain");
        List<Bridge> bridges = terrain.bridges();
        Objects.requireNonNull(bridges, "bridges");
        int totalVertices = 0;
        for (Bridge bridge : bridges) {
            Objects.requireNonNull(bridge, "bridge");
            totalVertices += VERTS_PER_BRIDGE;
        }
        float[] out = new float[totalVertices * STRIDE];
        int cur = 0;
        for (Bridge bridge : bridges) {
            cur = buildBridge(out, cur, bridge);
        }
        return new BuiltMesh(out, STRIDE);
    }

    private static int buildBridge(float[] out, int cur, Bridge bridge) {
        Vec2 s = bridge.start();
        Vec2 e = bridge.end();
        double dx = e.x() - s.x();
        double dz = e.z() - s.z();
        double len = StrictMath.hypot(dx, dz);
        if (len < 1.0e-9) {
            dx = 1.0;
            dz = 0.0;
            len = 1.0;
        }
        double dnx = dx / len;
        double dnz = dz / len;
        double px = -dnz;
        double pz = dnx;

        double hw = bridge.width() * 0.5;
        double deckY = bridge.deckHeight();
        double thickness = Math.max(1.0, bridge.width() * 0.2);
        double deckTop = deckY + thickness;
        double railHalf = 0.5;
        double railHeight = 1.5;
        double railTop = deckTop + railHeight;

        // deck
        float[][] deck = boxCorners(s.x(), s.z(), e.x(), e.z(), 0.0, 0.0, hw, px, pz, deckY, deckTop);
        cur = appendBox(out, cur, deck, dnx, dnz, px, pz);

        // + side rail
        float[][] railPos = boxCorners(s.x(), s.z(), e.x(), e.z(), px * hw, pz * hw, railHalf, px, pz, deckTop, railTop);
        cur = appendBox(out, cur, railPos, dnx, dnz, px, pz);
        // - side rail
        float[][] railNeg = boxCorners(s.x(), s.z(), e.x(), e.z(), -px * hw, -pz * hw, railHalf, px, pz, deckTop, railTop);
        cur = appendBox(out, cur, railNeg, dnx, dnz, px, pz);

        // pillars at t = 0, 0.5, 1 from the water surface (y=0) up to the deck
        for (double t : new double[] { 0.0, 0.5, 1.0 }) {
            double cx = s.x() + (e.x() - s.x()) * t;
            double cz = s.z() + (e.z() - s.z()) * t;
            double ha = railHalf;
            float[][] pillar = boxCorners(
                    cx - dnx * ha, cz - dnz * ha,
                    cx + dnx * ha, cz + dnz * ha,
                    0.0, 0.0, railHalf, px, pz, 0.0, deckY);
            cur = appendBox(out, cur, pillar, dnx, dnz, px, pz);
        }
        return cur;
    }

    /**
     * Computes the 8 corners of a box.
     *
     * <p>Corners are indexed {@code a*4 + s*2 + v} where {@code a} is the
     * along-axis (0 = first endpoint, 1 = second), {@code s} the across-side
     * (0 = negative, 1 = positive) and {@code v} the vertical level
     * (0 = bottom, 1 = top).
     */
    private static float[][] boxCorners(double ax0, double az0, double ax1, double az1,
                                        double ox, double oz, double hs, double px, double pz,
                                        double y0, double y1) {
        float[][] c = new float[8][3];
        double[] ax = { ax0, ax1 };
        double[] az = { az0, az1 };
        for (int a = 0; a < 2; a++) {
            for (int s = 0; s < 2; s++) {
                for (int v = 0; v < 2; v++) {
                    int idx = a * 4 + s * 2 + v;
                    double off = (s == 1) ? hs : -hs;
                    c[idx][0] = (float) (ax[a] + ox + px * off);
                    c[idx][1] = (float) (v == 1 ? y1 : y0);
                    c[idx][2] = (float) (az[a] + oz + pz * off);
                }
            }
        }
        return c;
    }

    /** Appends the 36 vertices (6 faces) of a box whose corners are {@code c}. */
    private static int appendBox(float[] out, int cur, float[][] c,
                                 double dnx, double dnz, double px, double pz) {
        cur = appendFace(out, cur, new int[] { 0, 4, 6, 2 }, c, 0.0, -1.0, 0.0);
        cur = appendFace(out, cur, new int[] { 1, 5, 7, 3 }, c, 0.0, 1.0, 0.0);
        cur = appendFace(out, cur, new int[] { 0, 2, 3, 1 }, c, -dnx, 0.0, -dnz);
        cur = appendFace(out, cur, new int[] { 4, 7, 6, 5 }, c, dnx, 0.0, dnz);
        cur = appendFace(out, cur, new int[] { 0, 1, 5, 4 }, c, -px, 0.0, -pz);
        cur = appendFace(out, cur, new int[] { 2, 3, 7, 6 }, c, px, 0.0, pz);
        return cur;
    }

    /** Appends one quad (4 corner indices) as two triangles (6 vertices). */
    private static int appendFace(float[] out, int cur, int[] quad, float[][] c,
                                  double nx, double ny, double nz) {
        int[] order = { quad[0], quad[1], quad[2], quad[0], quad[2], quad[3] };
        for (int idx : order) {
            out[cur++] = c[idx][0];
            out[cur++] = c[idx][1];
            out[cur++] = c[idx][2];
            out[cur++] = (float) nx;
            out[cur++] = (float) ny;
            out[cur++] = (float) nz;
        }
        return cur;
    }
}