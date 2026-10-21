package ua.battlesim.gfx.core;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for the non-finite view-projection matrix crash.
 *
 * <p>Symptom: the HUD projection threw
 * {@code "screen position must be finite: ..., -Infinity"} because the camera's
 * view/projection matrices grew without bound over time.
 *
 * <p>Root cause: the matrices were rebuilt in-place into the same buffers, and
 * in this joml build {@code lookAt}/{@code perspective} post-multiply rather than
 * reset, so every rebuild compounded the previous values until they overflowed to
 * +/-Infinity.
 *
 * <p>Fix: {@code OrbitCamera.updateMatrices()} normalises each buffer to identity
 * before rebuilding, making the rebuild idempotent. These tests assert that
 * repeated rebuilds (the per-frame {@code setAspect} path and the full interaction
 * set) keep the matrices finite, and that a fixed camera state yields an identical
 * matrix every rebuild.
 */
class OrbitCameraMatrixStabilityTest {

    private static boolean allFinite(Matrix4f m) {
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                if (!Float.isFinite(m.get(r, c))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void assertMatrixEquals(Matrix4f expected, Matrix4f actual, String what) {
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                assertEquals(expected.get(r, c), actual.get(r, c), 1e-6f,
                        what + " element [" + r + "][" + c + "] must be idempotent");
            }
        }
    }

    /** The per-frame setAspect() path must rebuild identical, finite matrices. */
    @Test
    void repeatedAspectRebuildsAreIdempotentAndFinite() {
        OrbitCamera cam = new OrbitCamera();
        Matrix4f baseline = new Matrix4f(cam.viewProjectionMatrix());

        for (int i = 0; i < 10_000; i++) {
            cam.setAspect(16.0f / 9.0f);
        }

        Matrix4f after = cam.viewProjectionMatrix();
        assertTrue(allFinite(after), "viewProjectionMatrix must remain finite after repeated rebuilds");
        assertMatrixEquals(baseline, after, "viewProjectionMatrix");
        assertTrue(allFinite(cam.viewMatrix()), "viewMatrix must remain finite");
        assertTrue(allFinite(cam.projectionMatrix()), "projectionMatrix must remain finite");
    }

    /** A full interaction sequence (pan/rotate/zoom/follow/reset) must stay finite. */
    @Test
    void interactionSequenceKeepsMatricesFinite() {
        OrbitCamera cam = new OrbitCamera(
                1000.0f, 0.0f, 1000.0f,
                1500.0f, 0.785f, 1.5f,
                (float) (StrictMath.PI / 3.0), 16.0f / 9.0f,
                0.1f, 20000.0f,
                20.0f, 6000.0f, 0.05f, (float) (StrictMath.PI - 0.05f));

        for (int i = 0; i < 2_000; i++) {
            cam.pan(10.0f, 5.0f);
            cam.rotate(0.01f, 0.005f);
            cam.zoom(0.1f);
            cam.follow(500.0f, 0.0f, 500.0f);
            cam.reset();
            cam.setAspect(16.0f / 9.0f);
        }

        assertTrue(allFinite(cam.viewMatrix()), "viewMatrix must remain finite across interactions");
        assertTrue(allFinite(cam.projectionMatrix()), "projectionMatrix must remain finite across interactions");
        assertTrue(allFinite(cam.viewProjectionMatrix()), "viewProjectionMatrix must remain finite across interactions");
        assertTrue(allFinite(cam.inverseViewProjectionMatrix()), "inverseViewProjectionMatrix must remain finite across interactions");
    }
}
