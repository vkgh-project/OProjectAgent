package ua.battlesim.gfx.core;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OrbitCamera} math (no GPU required).
 */
class OrbitCameraTest {

    @Test
    void defaultConstructor_hasExpectedDefaults() {
        OrbitCamera cam = new OrbitCamera();
        assertEquals(0.0f, cam.targetX(), 1e-6);
        assertEquals(0.0f, cam.targetY(), 1e-6);
        assertEquals(0.0f, cam.targetZ(), 1e-6);
        assertEquals(500.0f, cam.radius(), 1e-3);
        assertEquals(0.0f, cam.theta(), 1e-6);
        assertEquals((float) (StrictMath.PI / 4.0), cam.phi(), 1e-4);
    }

    @Test
    void eyePosition_theta0_phi90_directlyAbove() {
        OrbitCamera cam = new OrbitCamera(0, 0, 0, 100.0f, 0, (float) (StrictMath.PI / 2.0),
                (float) (StrictMath.PI / 3.0), 1.0f, 0.1f, 1000.0f,
                1.0f, 10000.0f, 0.05f, (float) (StrictMath.PI - 0.05f));
        Vector3f eye = cam.eyePosition();
        assertEquals(0.0f, eye.x(), 1e-3);
        assertEquals(100.0f, eye.y(), 1e-3);
        assertEquals(0.0f, eye.z(), 1e-3);
    }

    @Test
    void eyePosition_theta0_phi45() {
        OrbitCamera cam = new OrbitCamera(0, 0, 0, 100.0f, 0, (float) (StrictMath.PI / 4.0),
                (float) (StrictMath.PI / 3.0), 1.0f, 0.1f, 1000.0f,
                1.0f, 10000.0f, 0.05f, (float) (StrictMath.PI - 0.05f));
        Vector3f eye = cam.eyePosition();
        float expectedX = 100.0f * (float) StrictMath.cos(StrictMath.PI / 4.0);
        float expectedY = 100.0f * (float) StrictMath.sin(StrictMath.PI / 4.0);
        assertEquals(expectedX, eye.x(), 1e-3);
        assertEquals(expectedY, eye.y(), 1e-3);
        assertEquals(0.0f, eye.z(), 1e-3);
    }

    @Test
    void rotate_changesThetaAndPhi() {
        OrbitCamera cam = new OrbitCamera();
        float origTheta = cam.theta();
        float origPhi = cam.phi();
        cam.rotate(0.1f, 0.05f);
        assertEquals(origTheta + 0.1f, cam.theta(), 1e-6);
        assertEquals(origPhi + 0.05f, cam.phi(), 1e-6);
    }

    @Test
    void rotate_phiClampedToMax() {
        OrbitCamera cam = new OrbitCamera(0, 0, 0, 500.0f, 0, (float) (StrictMath.PI - 0.1),
                (float) (StrictMath.PI / 3.0), 1.0f, 0.1f, 1000.0f,
                10.0f, 10000.0f, 0.05f, (float) (StrictMath.PI - 0.05f));
        cam.rotate(0, 10.0f);
        assertTrue(cam.phi() <= (float) (StrictMath.PI - 0.05f));
    }

    @Test
    void rotate_phiClampedToMin() {
        OrbitCamera cam = new OrbitCamera(0, 0, 0, 500.0f, 0, 0.1f,
                (float) (StrictMath.PI / 3.0), 1.0f, 0.1f, 1000.0f,
                10.0f, 10000.0f, 0.05f, (float) (StrictMath.PI - 0.05f));
        cam.rotate(0, -10.0f);
        assertTrue(cam.phi() >= 0.05f);
    }

    @Test
    void zoom_inDecreasesRadius() {
        OrbitCamera cam = new OrbitCamera();
        float r0 = cam.radius();
        cam.zoom(1.0f);
        assertTrue(cam.radius() < r0);
    }

    @Test
    void zoom_outIncreasesRadius() {
        OrbitCamera cam = new OrbitCamera();
        float r0 = cam.radius();
        cam.zoom(-1.0f);
        assertTrue(cam.radius() > r0);
    }

    @Test
    void zoom_radiusClampedToMin() {
        OrbitCamera cam = new OrbitCamera(0, 0, 0, 50.0f, 0, (float) (StrictMath.PI / 4.0),
                (float) (StrictMath.PI / 3.0), 1.0f, 0.1f, 1000.0f,
                10.0f, 10000.0f, 0.05f, (float) (StrictMath.PI - 0.05f));
        cam.zoom(100.0f);
        assertTrue(cam.radius() >= 10.0f);
    }

    @Test
    void zoom_radiusClampedToMax() {
        OrbitCamera cam = new OrbitCamera(0, 0, 0, 5000.0f, 0, (float) (StrictMath.PI / 4.0),
                (float) (StrictMath.PI / 3.0), 1.0f, 0.1f, 1000.0f,
                10.0f, 8000.0f, 0.05f, (float) (StrictMath.PI - 0.05f));
        cam.zoom(-100.0f);
        assertTrue(cam.radius() <= 8000.0f);
    }

    @Test
    void pan_movesTarget() {
        OrbitCamera cam = new OrbitCamera();
        float ox = cam.targetX();
        cam.pan(10.0f, 5.0f);
        assertTrue(cam.targetX() != ox || cam.targetY() != 0.0f || cam.targetZ() != 0.0f);
    }

    @Test
    void follow_movesTarget() {
        OrbitCamera cam = new OrbitCamera();
        cam.follow(100.0f, 200.0f, 300.0f);
        assertEquals(100.0f, cam.targetX(), 1e-6);
        assertEquals(200.0f, cam.targetY(), 1e-6);
        assertEquals(300.0f, cam.targetZ(), 1e-6);
    }

    @Test
    void reset_restoresDefaults() {
        OrbitCamera cam = new OrbitCamera();
        cam.follow(100, 200, 300);
        cam.rotate(1.0f, 0.5f);
        cam.zoom(5.0f);
        cam.reset();
        assertEquals(0.0f, cam.targetX(), 1e-6);
        assertEquals(0.0f, cam.targetY(), 1e-6);
        assertEquals(0.0f, cam.targetZ(), 1e-6);
        assertEquals(500.0f, cam.radius(), 1e-3);
    }

    @Test
    void viewProjectionMatrix_isFinite() {
        OrbitCamera cam = new OrbitCamera();
        Matrix4f vp = cam.viewProjectionMatrix();
        for (int i = 0; i < 16; i++) {
            float v = vp.get(i / 4, i % 4);
            assertTrue(Float.isFinite(v), "VP element [" + i/4 + "][" + i%4 + "] = " + v);
        }
    }

    @Test
    void inverseViewProjection_roundTrip() {
        OrbitCamera cam = new OrbitCamera();
        Matrix4f vp = new Matrix4f(cam.viewProjectionMatrix());
        Matrix4f invVp = new Matrix4f(cam.inverseViewProjectionMatrix());
        Matrix4f product = new Matrix4f(vp).mul(invVp);
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                float expected = (r == c) ? 1.0f : 0.0f;
                // 1e-3: VP has elements up to farPlane (1e4) with a near plane of 0.1, so its
                // condition number is ~1e5 and a float round-trip (VP * VP^-1) legitimately
                // deviates from identity by ~1e-4..1e-3. 1e-4 was too tight and flaky.
                assertEquals(expected, product.get(r, c), 1e-3, "element [" + r + "][" + c + "]");
            }
        }
    }

    @Test
    void setAspect_updatesMatrix() {
        OrbitCamera cam = new OrbitCamera();
        float oldAspect = cam.aspect();
        cam.setAspect(4.0f / 3.0f);
        assertNotEquals(oldAspect, cam.aspect(), 1e-6);
        // Verify the (0,0) element of the projection matrix changed
        // In a perspective matrix, m00 = 1/(aspect * tan(fov/2))
        OrbitCamera cam2 = new OrbitCamera();
        cam2.setAspect(4.0f / 3.0f);
        float m00_a = cam2.projectionMatrix().get(0, 0);
        OrbitCamera cam3 = new OrbitCamera();
        cam3.setAspect(16.0f / 9.0f);
        float m00_b = cam3.projectionMatrix().get(0, 0);
        assertNotEquals(m00_a, m00_b, 1e-4);
    }

    @Test
    void constructor_invalidMinRadius_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrbitCamera(0, 0, 0, 500, 0, 1, 1, 1, 0.1f, 1000, -1.0f, 10000, 0, (float) StrictMath.PI));
    }

    @Test
    void constructor_invalidFov_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                new OrbitCamera(0, 0, 0, 500, 0, 1, 0.0f, 1, 0.1f, 1000, 1, 10000, 0, (float) StrictMath.PI));
    }

    @Test
    void follow_nonFinite_throws() {
        OrbitCamera cam = new OrbitCamera();
        assertThrows(IllegalArgumentException.class, () -> cam.follow(Float.NaN, 0, 0));
    }

    @Test
    void deterministic_sameOperations_sameMatrices() {
        OrbitCamera cam1 = new OrbitCamera();
        OrbitCamera cam2 = new OrbitCamera();
        cam1.rotate(0.5f, 0.3f);
        cam1.zoom(2.0f);
        cam1.pan(10.0f, -5.0f);
        cam2.rotate(0.5f, 0.3f);
        cam2.zoom(2.0f);
        cam2.pan(10.0f, -5.0f);
        assertEquals(cam1.viewProjectionMatrix(), cam2.viewProjectionMatrix());
    }
}
