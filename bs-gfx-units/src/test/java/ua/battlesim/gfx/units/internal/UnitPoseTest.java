package ua.battlesim.gfx.units.internal;

import ua.battlesim.api.Side;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.UnitView;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies pose computation from a {@link UnitView}: position/heading
 * interpolation with alpha, gun aim, recoil, charred/lying, selection and the
 * resolved side paint.
 */
class UnitPoseTest {

    private static final double EPS = 1.0e-9;

    private static UnitView view(int id, Side side, double x, double z, double prevX, double prevZ,
                                 double heading, double prevHeading,
                                 double aim, double prevAim, UnitState state, double odometer) {
        return new UnitView(id, side, UnitType.TANK,
                x, z, prevX, prevZ, heading, prevHeading, aim, prevAim, 100, state, odometer, -1);
    }

    @Test
    void position_interpolates_with_alpha() {
        TestTerrain t = new TestTerrain();
        UnitView u = view(1, Side.BLUE, 10, 0, 0, 0, 0, 0, 0, 0, UnitState.READY, 0);
        assertEquals(0.0, UnitPose.from(u, 0, 0.0, t, Optional.empty()).x(), EPS);
        assertEquals(10.0, UnitPose.from(u, 0, 1.0, t, Optional.empty()).x(), EPS);
        assertEquals(5.0, UnitPose.from(u, 0, 0.5, t, Optional.empty()).x(), EPS);
    }

    @Test
    void heading_interpolates_with_shortestArc() {
        TestTerrain t = new TestTerrain();
        double a = StrictMath.PI - 0.1;
        double b = -(StrictMath.PI - 0.1);
        UnitView u = view(1, Side.BLUE, 0, 0, 0, 0, b, a, 0, 0, UnitState.READY, 0);
        assertEquals(StrictMath.PI, UnitPose.from(u, 0, 0.5, t, Optional.empty()).yaw(), 1e-6);
    }

    @Test
    void sits_onTerrainHeight() {
        TestTerrain t = new TestTerrain();
        t.base = 7.0;
        UnitView u = view(1, Side.BLUE, 3, 4, 0, 0, 0, 0, 0, 0, UnitState.READY, 0);
        assertEquals(7.0, UnitPose.from(u, 0, 1.0, t, Optional.empty()).y(), EPS);
    }

    @Test
    void wheelAngle_fromOdometer() {
        TestTerrain t = new TestTerrain();
        UnitView u = view(1, Side.BLUE, 0, 0, 0, 0, 0, 0, 0, 0, UnitState.MOVING, 50.0);
        assertEquals(100.0, UnitPose.from(u, 0, 1.0, t, Optional.empty()).wheelAngle(), EPS);
    }

    @Test
    void gun_alongHeadingWhileMoving() {
        TestTerrain t = new TestTerrain();
        UnitView u = view(1, Side.BLUE, 0, 0, 0, 0, 0.5, 0.5, 2.0, 2.0, UnitState.MOVING, 0);
        assertEquals(0.0, UnitPose.from(u, 0, 1.0, t, Optional.empty()).gunAngle(), EPS);
    }

    @Test
    void gun_towardTargetWhileAttacking() {
        TestTerrain t = new TestTerrain();
        UnitView u = view(1, Side.BLUE, 0, 0, 0, 0, 0.0, 0.0, 1.0, 1.0, UnitState.ATTACKING, 0);
        assertEquals(1.0, UnitPose.from(u, 0, 1.0, t, Optional.empty()).gunAngle(), 1e-9);
    }

    @Test
    void recoil_zeroUnlessAttacking() {
        TestTerrain t = new TestTerrain();
        UnitView ready = view(1, Side.BLUE, 0, 0, 0, 0, 0, 0, 0, 0, UnitState.READY, 0);
        assertEquals(0.0, UnitPose.from(ready, 0, 1.0, t, Optional.empty()).recoil(), EPS);
        UnitView firing = view(1, Side.BLUE, 0, 0, 0, 0, 0, 0, 0, 0, UnitState.ATTACKING, 0);
        double r = UnitPose.from(firing, 5, 1.0, t, Optional.empty()).recoil();
        assertTrue(r >= 0.0 && r <= 1.0);
    }

    @Test
    void destroyed_charredAndLying() {
        TestTerrain t = new TestTerrain();
        UnitView u = view(1, Side.BLUE, 0, 0, 0, 0, 0, 0, 0, 0, UnitState.DESTROYED, 0);
        UnitPose p = UnitPose.from(u, 0, 1.0, t, Optional.empty());
        assertTrue(p.charred());
        assertTrue(p.lying());
    }

    @Test
    void selection_flag() {
        TestTerrain t = new TestTerrain();
        UnitView u = view(7, Side.BLUE, 0, 0, 0, 0, 0, 0, 0, 0, UnitState.READY, 0);
        assertTrue(UnitPose.from(u, 0, 1.0, t, Optional.of(7)).selected());
        assertFalse(UnitPose.from(u, 0, 1.0, t, Optional.of(8)).selected());
        assertFalse(UnitPose.from(u, 0, 1.0, t, Optional.empty()).selected());
    }

    @Test
    void paint_matches_side() {
        TestTerrain t = new TestTerrain();
        UnitView blue = view(1, Side.BLUE, 0, 0, 0, 0, 0, 0, 0, 0, UnitState.READY, 0);
        UnitView red = view(1, Side.RED, 0, 0, 0, 0, 0, 0, 0, 0, UnitState.READY, 0);
        float[] pb = UnitPose.from(blue, 0, 1.0, t, Optional.empty()).paint();
        float[] pr = UnitPose.from(red, 0, 1.0, t, Optional.empty()).paint();
        assertTrue(pb[2] > pb[0], "blue side should be bluish");
        assertTrue(pr[0] > pr[2], "red side should be reddish");
    }
}
