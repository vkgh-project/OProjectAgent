package ua.battlesim.gfx.units.internal;

import ua.battlesim.api.UnitState;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the deterministic animation maths: angle lerp (incl. wrap-around),
 * rotation primitives, the local-to-world tilt matrix, and the per-unit
 * animation values (wheel, gun, recoil, legs).
 */
class UnitMathTest {

    private static final double EPS = 1.0e-9;

    @Test
    void lerp_endpoints_and_clamp() {
        assertEquals(2.0, UnitMath.lerp(2, 8, 0.0), EPS);
        assertEquals(8.0, UnitMath.lerp(2, 8, 1.0), EPS);
        assertEquals(5.0, UnitMath.lerp(2, 8, 0.5), EPS);
        assertEquals(2.0, UnitMath.lerp(2, 8, -1.0), EPS);
        assertEquals(8.0, UnitMath.lerp(2, 8, 2.0), EPS);
    }

    @Test
    void lerpAngle_shortestArc() {
        assertEquals(StrictMath.PI / 4, UnitMath.lerpAngle(0.0, StrictMath.PI / 2, 0.5), 1e-6);
    }

    @Test
    void lerpAngle_wrapsAround() {
        double a = StrictMath.PI - 0.1;
        double b = -(StrictMath.PI - 0.1);
        assertEquals(StrictMath.PI, UnitMath.lerpAngle(a, b, 0.5), 1e-6);
    }

    @Test
    void normalizeAngle_range() {
        assertEquals(StrictMath.PI, UnitMath.normalizeAngle(3.0 * StrictMath.PI), 1e-9);
        assertEquals(StrictMath.PI / 2, UnitMath.normalizeAngle(StrictMath.PI * 5 / 2), 1e-9);
        assertEquals(-StrictMath.PI + 0.2, UnitMath.normalizeAngle(StrictMath.PI + 0.2), 1e-9);
    }

    @Test
    void rotation_primitives() {
        float[] r90 = UnitMath.rotY(new float[] { 1, 0, 0 }, StrictMath.PI / 2);
        assertEquals(0.0f, r90[0], 1e-6f);
        assertEquals(1.0f, r90[2], 1e-6f);
        float[] rz = UnitMath.rotZ(new float[] { 1, 0, 0 }, StrictMath.PI / 2);
        assertEquals(0.0f, rz[0], 1e-6f);
        assertEquals(1.0f, rz[1], 1e-6f);
        float[] rx = UnitMath.rotX(new float[] { 0, 1, 0 }, StrictMath.PI / 2);
        assertEquals(0.0f, rx[1], 1e-6f);
        assertEquals(1.0f, rx[2], 1e-6f);
    }

    @Test
    void rotation_about_pivot() {
        float[] z = UnitMath.rotAboutZ(new float[] { 1, 1, 0 }, new float[] { 0, 1, 0 }, StrictMath.PI / 2);
        assertEquals(0.0f, z[0], 1e-6f);
        assertEquals(2.0f, z[1], 1e-6f);
        float[] y = UnitMath.rotAboutY(new float[] { 2, 0, 0 }, new float[] { 1, 0, 0 }, StrictMath.PI / 2);
        assertEquals(1.0f, y[0], 1e-6f);
        assertEquals(1.0f, y[2], 1e-6f);
    }

    @Test
    void rotationMatrix_flat_isIdentity() {
        float[] m = UnitMath.rotationMatrix(0.0, 0, 1, 0);
        float[] fx = UnitMath.applyMat(m, 1, 0, 0);
        assertEquals(1.0f, fx[0], 1e-6f);
        assertEquals(0.0f, fx[1], 1e-6f);
        assertEquals(0.0f, fx[2], 1e-6f);
        assertEquals(1.0f, UnitMath.applyMat(m, 0, 1, 0)[1], 1e-6f);
        assertEquals(1.0f, UnitMath.applyMat(m, 0, 0, 1)[2], 1e-6f);
    }

    @Test
    void rotationMatrix_yaw_turnsForward() {
        float[] m = UnitMath.rotationMatrix(StrictMath.PI / 2, 0, 1, 0);
        float[] fx = UnitMath.applyMat(m, 1, 0, 0);
        assertEquals(0.0f, fx[0], 1e-6f);
        assertEquals(1.0f, fx[2], 1e-6f);
        assertEquals(1.0f, UnitMath.applyMat(m, 0, 1, 0)[1], 1e-6f);
    }

    @Test
    void rotationMatrix_tiltsToSlope() {
        double n = StrictMath.sqrt(0.5);
        float[] m = UnitMath.rotationMatrix(0.0, -n, n, 0.0);
        float[] up = UnitMath.applyMat(m, 0, 1, 0);
        assertEquals(-n, up[0], 1e-4);
        assertEquals(n, up[1], 1e-4);
        assertEquals(0.0f, up[2], 1e-4);
        assertTrue(UnitMath.applyMat(m, 1, 0, 0)[1] > 0.0f, "forward should tilt up the slope");
    }

    @Test
    void normalAt_flat_and_slope() {
        TestTerrain flat = new TestTerrain();
        double[] n0 = UnitMath.normalAt(flat, 10, 10);
        assertEquals(0.0, n0[0], 1e-12);
        assertEquals(1.0, n0[1], 1e-12);
        assertEquals(0.0, n0[2], 1e-12);

        TestTerrain slope = new TestTerrain();
        slope.slopeX = 1.0;
        double[] n1 = UnitMath.normalAt(slope, 0, 0);
        assertEquals(-StrictMath.sqrt(0.5), n1[0], 1e-9);
        assertEquals(StrictMath.sqrt(0.5), n1[1], 1e-9);
        assertEquals(0.0, n1[2], 1e-12);
    }

    @Test
    void wheelAngle_fromOdometer() {
        assertEquals(100.0, UnitMath.wheelAngle(50.0), EPS);
        assertEquals(0.0, UnitMath.wheelAngle(0.0), EPS);
    }

    @Test
    void gunAngle_alongHeadingWhenMoving() {
        assertEquals(0.0, UnitMath.gunAngle(UnitState.MOVING, 0.5, 2.0), EPS);
        assertEquals(0.0, UnitMath.gunAngle(UnitState.READY, 0.5, 2.0), EPS);
        assertEquals(0.0, UnitMath.gunAngle(UnitState.WAITING, 0.5, 2.0), EPS);
    }

    @Test
    void gunAngle_towardTargetWhenAttacking() {
        assertEquals(1.0, UnitMath.gunAngle(UnitState.ATTACKING, 0.0, 1.0), 1e-9);
        assertEquals(-0.4, UnitMath.gunAngle(UnitState.ATTACKING, 0.5, 0.1), 1e-9);
    }

    @Test
    void recoil_zeroUnlessAttacking_andBounded() {
        assertEquals(0.0, UnitMath.recoil(10, 3, UnitState.READY), EPS);
        assertEquals(0.0, UnitMath.recoil(10, 3, UnitState.MOVING), EPS);
        double r = UnitMath.recoil(10, 3, UnitState.ATTACKING);
        assertTrue(r >= 0.0 && r <= 1.0, "recoil must be in [0,1]: " + r);
        assertEquals(r, UnitMath.recoil(10, 3, UnitState.ATTACKING), 0.0);
    }

    @Test
    void legSwing_signAndPhase() {
        assertEquals(0.5, UnitMath.legSwing(StrictMath.PI / 2, 1.0), 1e-9);
        assertEquals(-0.5, UnitMath.legSwing(StrictMath.PI / 2, -1.0), 1e-9);
        assertEquals(0.0, UnitMath.legSwing(0.0, 1.0), EPS);
    }
}