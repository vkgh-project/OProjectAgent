package ua.battlesim.gfx.units.internal;

/**
 * Turns a {@link UnitModel} + {@link UnitPose} into world-space vertex data
 * (interleaved {@code pos(3) + normal(3) + color(4)}), splitting the opaque
 * geometry from the translucent shadow/marker geometry.
 *
 * <p>Per vertex the local part is animated (wheel spin, turret/gun aim, gun
 * recoil, leg swing), then a destroyed model is tipped over, then the whole
 * part is rotated by the unit's local-to-world matrix and translated to the
 * unit origin. Normals are rotated by the same matrix. Colours come from
 * {@link Paint} (with a scrolling tread pattern on tracks).
 *
 * <p>Pure CPU, deterministic, NaN-free; unit-testable without a GPU.
 */
public final class UnitVertexWriter {

    /** Floats per output vertex: pos(3) + normal(3) + color(4). */
    public static final int VERT_STRIDE = 10;

    /** Ground offset (metres) applied to shadows/markers so they do not z-fight. */
    static final float GROUND_LIFT = 0.04f;
    /** Barrels are pulled back this far (metres) at full recoil. */
    static final float RECOIL_TRAVEL = 0.25f;

    private UnitVertexWriter() {
    }

    /**
     * Writes the world-space vertices of one unit into {@code opaque} and
     * {@code shadow}.
     */
    public static void write(UnitModel model, UnitPose pose, FloatList opaque, FloatList shadow) {
        float[] M = UnitMath.rotationMatrix(pose.yaw(), pose.nx(), pose.ny(), pose.nz());
        for (Piece piece : model.pieces()) {
            if (piece.role == Role.MARKER && !pose.selected()) {
                continue;
            }
            FloatList out = (piece.role == Role.SHADOW || piece.role == Role.MARKER) ? shadow : opaque;
            writePiece(M, piece, pose, out);
        }
    }

    private static void writePiece(float[] M, Piece piece, UnitPose pose, FloatList out) {
        boolean ground = piece.role == Role.SHADOW || piece.role == Role.MARKER;
        boolean isTrack = piece.role == Role.TRACK;
        boolean animated = piece.role == Role.WHEEL || piece.role == Role.TURRET
                || piece.role == Role.GUN || piece.role == Role.LEG;
        boolean canLie = !ground;
        float[] pivot = { piece.px, piece.py, piece.pz };
        float[] baseColor = Paint.color(piece.role, pose.paint(), pose.charred(), pose.selected());
        float liftX = GROUND_LIFT * (float) pose.nx();
        float liftY = GROUND_LIFT * (float) pose.ny();
        float liftZ = GROUND_LIFT * (float) pose.nz();
        double wheelAngle = pose.wheelAngle();
        double gunAngle = pose.gunAngle();
        double recoil = pose.recoil();
        double legSwing = UnitMath.legSwing(pose.legPhase(), piece.animSign);

        float[] v = new float[3];
        float[] n = new float[3];
        int count = piece.vertexCount();
        for (int i = 0; i < count; i++) {
            int o = i * Piece.STRIDE;
            v[0] = piece.base[o];
            v[1] = piece.base[o + 1];
            v[2] = piece.base[o + 2];
            n[0] = piece.base[o + 3];
            n[1] = piece.base[o + 4];
            n[2] = piece.base[o + 5];

            if (animated) {
                v = partRotate(v, piece, pivot, gunAngle, wheelAngle, recoil, legSwing);
            }
            if (canLie && pose.lying()) {
                v = UnitMath.rotZ(v, UnitMath.LIE_ANGLE);
                n = UnitMath.rotZ(n, UnitMath.LIE_ANGLE);
            }

            float[] w = UnitMath.applyMat(M, v[0], v[1], v[2]);
            float wx = w[0] + (float) pose.x();
            float wy = w[1] + (float) pose.y();
            float wz = w[2] + (float) pose.z();
            if (ground) {
                wx += liftX;
                wy += liftY;
                wz += liftZ;
            }
            float[] nw = UnitMath.normalize3(UnitMath.applyMat(M, n[0], n[1], n[2]));

            float r = baseColor[0];
            float g = baseColor[1];
            float b = baseColor[2];
            if (isTrack) {
                float phase = (float) (StrictMath.PI * 2.0 * (v[0] / 0.6 + pose.treadScroll()));
                float stripe = 0.5f + 0.5f * (float) StrictMath.sin(phase);
                float f = 0.55f + 0.5f * stripe;
                r *= f;
                g *= f;
                b *= f;
            }

            out.add(wx);
            out.add(wy);
            out.add(wz);
            out.add(nw[0]);
            out.add(nw[1]);
            out.add(nw[2]);
            out.add(r);
            out.add(g);
            out.add(b);
            out.add(baseColor[3]);
        }
    }

    /** Applies the role-specific part rotation (about the piece pivot). */
    private static float[] partRotate(float[] v, Piece piece, float[] pivot,
                                      double gunAngle, double wheelAngle,
                                      double recoil, double legSwing) {
        switch (piece.role) {
            case WHEEL:
                return UnitMath.rotAboutZ(v, pivot, wheelAngle);
            case TURRET:
                return UnitMath.rotAboutY(v, pivot, gunAngle);
            case GUN: {
                float[] pulled = { v[0] - (float) (recoil * RECOIL_TRAVEL), v[1], v[2] };
                return UnitMath.rotAboutY(pulled, pivot, gunAngle);
            }
            case LEG:
                return UnitMath.rotAboutZ(v, pivot, legSwing);
            default:
                return v;
        }
    }
}
