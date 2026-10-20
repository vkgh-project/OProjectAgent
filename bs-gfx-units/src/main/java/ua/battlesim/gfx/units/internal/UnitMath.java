package ua.battlesim.gfx.units.internal;

import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitState;

/**
 * Deterministic animation maths for the unit renderer.
 *
 * <p>All trigonometry uses {@link StrictMath}. There is no wall clock and no
 * unseeded randomness: every value is a pure function of its arguments, so the
 * whole class is unit-testable without a GPU.
 *
 * <p>Angle convention: heading is the angle of the {@code (cos, sin)} vector in
 * the (x, z) plane, in radians; positive rotation about {@code Y} carries the
 * local forward {@code +X} toward {@code +Z} (south).
 */
public final class UnitMath {

    /** Kinematic wheel radius in metres (drives wheel spin and tread scroll). */
    public static final double WHEEL_RADIUS = 0.5;
    /** Infantry leg swing amplitude in radians. */
    public static final double SWING_AMP = 0.5;
    /** Angle (radians) a destroyed model tips over onto the ground. */
    public static final double LIE_ANGLE = 1.35;

    private static final double TWO_PI = 2 * StrictMath.PI;

    private UnitMath() {
    }

    public static double clamp01(double t) {
        return t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
    }

    /** Linear interpolation with {@code t} clamped to {@code [0, 1]}. */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp01(t);
    }

    /** Normalises an angle to the half-open range {@code (-PI, PI]}. */
    public static double normalizeAngle(double a) {
        a = a % TWO_PI;
        if (a > StrictMath.PI) {
            a -= TWO_PI;
        }
        if (a <= -StrictMath.PI) {
            a += TWO_PI;
        }
        return a;
    }

    /** Interpolates between two angles along the shortest arc, with {@code t} clamped to {@code [0, 1]}. */
    public static double lerpAngle(double a, double b, double t) {
        double delta = normalizeAngle(b - a);
        return a + delta * clamp01(t);
    }

    /** Wheel rotation angle (radians) from the odometer. */
    public static double wheelAngle(double odometer) {
        return odometer / WHEEL_RADIUS;
    }

    /**
     * Turret / gun aim relative to the unit heading. The gun points along the
     * heading while moving / ready / waiting ({@code 0}) and swings toward the
     * target only while attacking.
     */
    public static double gunAngle(UnitState state, double yaw, double aim) {
        if (state == UnitState.ATTACKING) {
            return normalizeAngle(aim - yaw);
        }
        return 0.0;
    }

    /** Barrel recoil in {@code [0, 1]}: a deterministic pulse (tick and id) while attacking, else {@code 0}. */
    public static double recoil(int tick, int id, UnitState state) {
        if (state != UnitState.ATTACKING) {
            return 0.0;
        }
        double phase = TWO_PI * (tick * 0.5 + id * 0.13);
        return 0.5 * (1.0 + StrictMath.sin(phase));
    }

    /** Walk-cycle phase (radians) from the odometer. */
    public static double legPhase(double odometer) {
        return odometer * 1.3;
    }

    /** Signed leg swing (radians) for a given phase and leg sign. */
    public static double legSwing(double legPhase, double sign) {
        return SWING_AMP * StrictMath.sin(legPhase) * sign;
    }

    // --- rotations (operating on float[3]) ---

    /** Rotates a vector about the local {@code X} axis. */
    public static float[] rotX(float[] v, double a) {
        double c = StrictMath.cos(a), s = StrictMath.sin(a);
        double y = v[1] * c - v[2] * s;
        double z = v[1] * s + v[2] * c;
        return new float[] { v[0], (float) y, (float) z };
    }

    /** Rotates a vector about the local {@code Y} axis. */
    public static float[] rotY(float[] v, double a) {
        double c = StrictMath.cos(a), s = StrictMath.sin(a);
        double x = v[0] * c - v[2] * s;
        double z = v[0] * s + v[2] * c;
        return new float[] { (float) x, v[1], (float) z };
    }

    /** Rotates a vector about the local {@code Z} axis. */
    public static float[] rotZ(float[] v, double a) {
        double c = StrictMath.cos(a), s = StrictMath.sin(a);
        double x = v[0] * c - v[1] * s;
        double y = v[0] * s + v[1] * c;
        return new float[] { (float) x, (float) y, v[2] };
    }

    /** Rotates a vector about the {@code Y} axis through {@code pivot}. */
    public static float[] rotAboutY(float[] v, float[] pivot, double a) {
        float[] d = rotY(new float[] { v[0] - pivot[0], v[1] - pivot[1], v[2] - pivot[2] }, a);
        return new float[] { d[0] + pivot[0], d[1] + pivot[1], d[2] + pivot[2] };
    }

    /** Rotates a vector about the {@code Z} axis through {@code pivot}. */
    public static float[] rotAboutZ(float[] v, float[] pivot, double a) {
        float[] d = rotZ(new float[] { v[0] - pivot[0], v[1] - pivot[1], v[2] - pivot[2] }, a);
        return new float[] { d[0] + pivot[0], d[1] + pivot[1], d[2] + pivot[2] };
    }

    /**
     * Builds the local-to-world rotation matrix (row-major {@code 3x3}) that
     * orients a unit: its local up aligns with the terrain normal
     * {@code (nx, ny, nz)} and its local forward projects onto the tangent
     * plane along the heading direction {@code (cos yaw, 0, sin yaw)}.
     *
     * @return nine floats, row-major
     */
    public static float[] rotationMatrix(double yaw, double nx, double ny, double nz) {
        double c = StrictMath.cos(yaw), s = StrictMath.sin(yaw);
        double dx = c, dy = 0.0, dz = s;
        double dot = dx * nx + dy * ny + dz * nz;
        double fx = dx - dot * nx;
        double fy = dy - dot * ny;
        double fz = dz - dot * nz;
        double fl = StrictMath.sqrt(fx * fx + fy * fy + fz * fz);
        if (fl < 1.0e-6) {
            fx = dx;
            fy = dy;
            fz = dz;
        } else {
            fx /= fl;
            fy /= fl;
            fz /= fl;
        }
        double rx = fy * nz - fz * ny;
        double ry = fz * nx - fx * nz;
        double rz = fx * ny - fy * nx;
        return new float[] {
                (float) fx, (float) nx, (float) rx,
                (float) fy, (float) ny, (float) ry,
                (float) fz, (float) nz, (float) rz
        };
    }

    /** Applies a row-major {@code 3x3} matrix to a vector. */
    public static float[] applyMat(float[] m, float x, float y, float z) {
        return new float[] {
                m[0] * x + m[1] * y + m[2] * z,
                m[3] * x + m[4] * y + m[5] * z,
                m[6] * x + m[7] * y + m[8] * z
        };
    }

    /** Normalises a vector (falls back to up for a zero vector). */
    public static float[] normalize3(float[] v) {
        double l = StrictMath.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (l < 1.0e-9) {
            return new float[] { 0f, 1f, 0f };
        }
        return new float[] { (float) (v[0] / l), (float) (v[1] / l), (float) (v[2] / l) };
    }

    /**
     * Computes the terrain surface normal at {@code (x, z)} by central
     * differences of {@link Terrain#heightAt(double, double)}.
     *
     * @return a unit vector in world space ({@code x} east, {@code y} up, {@code z} south)
     */
    public static double[] normalAt(Terrain terrain, double x, double z) {
        double d = 1.0;
        double hx1 = terrain.heightAt(x + d, z);
        double hx0 = terrain.heightAt(x - d, z);
        double hz1 = terrain.heightAt(x, z + d);
        double hz0 = terrain.heightAt(x, z - d);
        double slopeX = (hx1 - hx0) / (2.0 * d);
        double slopeZ = (hz1 - hz0) / (2.0 * d);
        double nx = -slopeX;
        double ny = 1.0;
        double nz = -slopeZ;
        double len = StrictMath.sqrt(nx * nx + ny * ny + nz * nz);
        return new double[] { nx / len, ny / len, nz / len };
    }
}