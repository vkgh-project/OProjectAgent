package ua.battlesim.gfx.core;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Orbit camera with pan, rotate, zoom, follow-target, clamps, and
 * view-projection matrix computation.
 *
 * <p>All trigonometry uses {@link StrictMath} for determinism.
 *
 * <p>Coordinate system: x = east, y = up, z = south.
 *
 * <p>Thread-safety: not thread-safe; use from a single thread.
 */
public final class OrbitCamera {

    private static final float DEFAULT_RADIUS = 500.0f;
    private static final float DEFAULT_THETA = 0.0f;
    private static final float DEFAULT_PHI = (float) (StrictMath.PI / 4.0);
    private static final float DEFAULT_FOV = (float) (StrictMath.PI / 3.0);
    private static final float DEFAULT_ASPECT = 16.0f / 9.0f;
    private static final float DEFAULT_NEAR = 0.1f;
    private static final float DEFAULT_FAR = 10000.0f;
    private static final float DEFAULT_MIN_RADIUS = 10.0f;
    private static final float DEFAULT_MAX_RADIUS = 10000.0f;
    private static final float DEFAULT_MIN_PHI = 0.05f;
    private static final float DEFAULT_MAX_PHI = (float) (StrictMath.PI - 0.05f);

    private float targetX, targetY, targetZ;
    private float radius;
    private float theta;
    private float phi;
    private float fovY;
    private float aspect;
    private float nearPlane;
    private float farPlane;

    private final float minRadius;
    private final float maxRadius;
    private final float minPhi;
    private final float maxPhi;

    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();
    private final Matrix4f viewProjectionMatrix = new Matrix4f();
    private final Matrix4f invViewProjectionMatrix = new Matrix4f();

    /**
     * Creates a camera with default parameters: target at origin,
     * radius 500 m, looking down at 45 degrees.
     */
    public OrbitCamera() {
        this(0.0f, 0.0f, 0.0f, DEFAULT_RADIUS, DEFAULT_THETA, DEFAULT_PHI,
                DEFAULT_FOV, DEFAULT_ASPECT, DEFAULT_NEAR, DEFAULT_FAR,
                DEFAULT_MIN_RADIUS, DEFAULT_MAX_RADIUS, DEFAULT_MIN_PHI, DEFAULT_MAX_PHI);
    }

    /**
     * Creates a camera with the given parameters.
     */
    public OrbitCamera(float targetX, float targetY, float targetZ,
                       float radius, float theta, float phi,
                       float fovY, float aspect, float nearPlane, float farPlane,
                       float minRadius, float maxRadius, float minPhi, float maxPhi) {
        if (!Float.isFinite(minRadius) || minRadius <= 0.0f)
            throw new IllegalArgumentException("minRadius must be finite and > 0: " + minRadius);
        if (!Float.isFinite(maxRadius) || maxRadius < minRadius)
            throw new IllegalArgumentException("maxRadius must be finite and >= minRadius: " + maxRadius);
        if (!Float.isFinite(minPhi) || minPhi < 0.0f)
            throw new IllegalArgumentException("minPhi must be finite and >= 0: " + minPhi);
        if (!Float.isFinite(maxPhi) || maxPhi > StrictMath.PI)
            throw new IllegalArgumentException("maxPhi must be finite and <= PI: " + maxPhi);
        if (maxPhi < minPhi)
            throw new IllegalArgumentException("maxPhi must be >= minPhi: " + maxPhi + " < " + minPhi);
        if (!Float.isFinite(fovY) || fovY <= 0.0f || fovY >= StrictMath.PI)
            throw new IllegalArgumentException("fovY must be finite and in (0, PI): " + fovY);
        if (!Float.isFinite(aspect) || aspect <= 0.0f)
            throw new IllegalArgumentException("aspect must be finite and > 0: " + aspect);
        if (!Float.isFinite(nearPlane) || nearPlane <= 0.0f)
            throw new IllegalArgumentException("nearPlane must be finite and > 0: " + nearPlane);
        if (!Float.isFinite(farPlane) || farPlane <= nearPlane)
            throw new IllegalArgumentException("farPlane must be finite and > nearPlane: " + farPlane);

        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.radius = clamp(radius, minRadius, maxRadius);
        this.theta = theta;
        this.phi = clamp(phi, minPhi, maxPhi);
        this.fovY = fovY;
        this.aspect = aspect;
        this.nearPlane = nearPlane;
        this.farPlane = farPlane;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.minPhi = minPhi;
        this.maxPhi = maxPhi;

        updateMatrices();
    }

    /**
     * Pans the camera target in the camera's local plane.
     *
     * @param dx horizontal displacement in world units (positive = camera-right)
     * @param dy vertical displacement in world units (positive = camera-up)
     */
    public void pan(float dx, float dy) {
        Vector3f eye = eyePosition();
        Vector3f forward = new Vector3f(targetX - eye.x(), targetY - eye.y(), targetZ - eye.z()).normalize();
        Vector3f worldUp = new Vector3f(0.0f, 1.0f, 0.0f);
        Vector3f right = new Vector3f(forward).cross(worldUp).normalize();
        Vector3f camUp = new Vector3f(right).cross(forward).normalize();

        targetX += dx * right.x() + dy * camUp.x();
        targetY += dx * right.y() + dy * camUp.y();
        targetZ += dx * right.z() + dy * camUp.z();

        updateMatrices();
    }

    /**
     * Rotates the camera around the target.
     *
     * @param dTheta change in azimuth in radians
     * @param dPhi   change in elevation in radians
     */
    public void rotate(float dTheta, float dPhi) {
        theta += dTheta;
        phi = clamp(phi + dPhi, minPhi, maxPhi);
        updateMatrices();
    }

    /**
     * Zooms the camera (exponentially).
     *
     * @param delta positive = zoom in (decrease radius), negative = zoom out
     */
    public void zoom(float delta) {
        float factor = (float) StrictMath.exp(-delta * 0.1);
        radius = clamp(radius * factor, minRadius, maxRadius);
        updateMatrices();
    }

    /**
     * Moves the camera target to the given world position (follow).
     */
    public void follow(float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
            throw new IllegalArgumentException("target must be finite: " + x + ", " + y + ", " + z);
        targetX = x;
        targetY = y;
        targetZ = z;
        updateMatrices();
    }

    /** Resets the camera to its initial default state. */
    public void reset() {
        targetX = 0.0f;
        targetY = 0.0f;
        targetZ = 0.0f;
        radius = DEFAULT_RADIUS;
        theta = DEFAULT_THETA;
        phi = DEFAULT_PHI;
        updateMatrices();
    }

    /**
     * Sets the aspect ratio (viewport width / height).
     */
    public void setAspect(float aspect) {
        if (!Float.isFinite(aspect) || aspect <= 0.0f)
            throw new IllegalArgumentException("aspect must be finite and > 0: " + aspect);
        this.aspect = aspect;
        updateMatrices();
    }

    // --- Getters ---

    /** @return target x in metres */
    public float targetX() { return targetX; }

    /** @return target y in metres */
    public float targetY() { return targetY; }

    /** @return target z in metres */
    public float targetZ() { return targetZ; }

    /** @return current orbit radius in metres */
    public float radius() { return radius; }

    /** @return current azimuth in radians */
    public float theta() { return theta; }

    /** @return current elevation in radians */
    public float phi() { return phi; }

    /** @return vertical field of view in radians */
    public float fovY() { return fovY; }

    /** @return current aspect ratio */
    public float aspect() { return aspect; }

    /** @return minimum orbit radius clamp */
    public float minRadius() { return minRadius; }

    /** @return maximum orbit radius clamp */
    public float maxRadius() { return maxRadius; }

    /** @return minimum elevation clamp */
    public float minPhi() { return minPhi; }

    /** @return maximum elevation clamp */
    public float maxPhi() { return maxPhi; }

    /** @return the view matrix (world-to-eye) */
    public Matrix4f viewMatrix() { return viewMatrix; }

    /** @return the perspective projection matrix */
    public Matrix4f projectionMatrix() { return projectionMatrix; }

    /** @return the combined view-projection matrix (P x V) */
    public Matrix4f viewProjectionMatrix() { return viewProjectionMatrix; }

    /** @return the inverse of the view-projection matrix */
    public Matrix4f inverseViewProjectionMatrix() { return invViewProjectionMatrix; }

    /**
     * Returns the camera eye position in world space.
     *
     * @return a new {@link Vector3f} with the eye position
     */
    public Vector3f eyePosition() {
        float cosPhi = (float) StrictMath.cos(phi);
        float sinPhi = (float) StrictMath.sin(phi);
        float cosTheta = (float) StrictMath.cos(theta);
        float sinTheta = (float) StrictMath.sin(theta);
        return new Vector3f(
                targetX + radius * cosPhi * cosTheta,
                targetY + radius * sinPhi,
                targetZ + radius * cosPhi * sinTheta
        );
    }

    // --- Internal ---

    private void updateMatrices() {
        Vector3f eye = eyePosition();
        // IMPORTANT (bugfix): in this joml build Matrix4f.perspective / lookAt act as a
        // post-multiply (they append into the existing matrix content instead of setting
        // it from scratch). Reusing the same Matrix4f buffer therefore compounded every
        // updateMatrices() call: since setAspect() runs every frame and every interaction
        // (pan/rotate/zoom/follow/reset) also calls it, viewMatrix & projectionMatrix
        // multiplied into themselves frame after frame until they overflowed to +/-Infinity.
        // That non-finite view-projection matrix then made the HUD projection (w division)
        // produce -Infinity -> LayoutMath.markerRect crashed with "screen position must be
        // finite: ..., -Infinity". Fix: normalize each buffer to identity first so the
        // rebuild is idempotent (same camera state -> same matrices), regardless of whether
        // the joml methods set or append.
        viewMatrix.identity().lookAt(eye.x(), eye.y(), eye.z(), targetX, targetY, targetZ, 0.0f, 1.0f, 0.0f);
        projectionMatrix.identity().perspective(fovY, aspect, nearPlane, farPlane);
        // We want viewProjectionMatrix = projectionMatrix * viewMatrix (P * V).
        //
        // joml's two-arg mul is: receiver.mul(a, b)  ==>  b = receiver * a
        // (the 2nd argument is the RESULT; verified empirically against joml 1.10.9:
        //  A.translate(1,2,3).mul(B, R) gives R's translation (1,2,3) = A*B, and
        //  B.mul(A, R) gives R's translation (10,40,90) = B*A).
        // So to get P*V we make the PROJECTION the receiver:
        projectionMatrix.mul(viewMatrix, viewProjectionMatrix);
        // invViewProjectionMatrix = (P * V)^-1. IMPORTANT: joml's invert(m) short-circuits
        // to "m.identity()" when the receiver is identity-flagged, which would WIPE the
        // freshly-computed viewProjectionMatrix. So copy it in via set() and invert() in
        // place (invert() = this^-1, never touches its argument).
        invViewProjectionMatrix.set(viewProjectionMatrix).invert();
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}