package ua.battlesim.ui.internal;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Screen-space projection and unprojection utilities.
 *
 * <p>Uses the view-projection matrix from {@link ua.battlesim.gfx.core.OrbitCamera}.
 *
 * <p>Coordinate system: world x = east, y = up, z = south.
 * Screen: x right, y down (top-left origin).
 *
 * <p>Deterministic: no randomness, no wall clock.
 */
public final class ScreenProjection {

    private ScreenProjection() {}

    /**
     * Projects a world-space point to screen coordinates.
     *
     * @param wx world x in metres
     * @param wy world y in metres
     * @param wz world z in metres
     * @param vp the view-projection matrix
     * @param viewportWidth viewport width in pixels
     * @param viewportHeight viewport height in pixels
     * @return a 2-element array {screenX, screenY} in pixels
     * @throws IllegalArgumentException if viewport dimensions are not positive
     */
    public static double[] project(double wx, double wy, double wz,
                                   Matrix4f vp, int viewportWidth, int viewportHeight) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("viewport must be > 0: " + viewportWidth + ", " + viewportHeight);
        }
        Vector4f clip = new Vector4f((float) wx, (float) wy, (float) wz, 1.0f);
        vp.transform(clip);
        if (clip.w() == 0.0f) {
            return new double[]{viewportWidth / 2.0, viewportHeight / 2.0};
        }
        double ndcX = clip.x() / clip.w();
        double ndcY = clip.y() / clip.w();
        double screenX = (ndcX + 1.0) / 2.0 * viewportWidth;
        double screenY = (1.0 - (ndcY + 1.0) / 2.0) * viewportHeight;
        return new double[]{screenX, screenY};
    }

    /**
     * Unprojects a screen point to a ray in world space.
     *
     * @param sx screen x in pixels
     * @param sy screen y in pixels
     * @param invVp the inverse view-projection matrix
     * @param viewportWidth viewport width in pixels
     * @param viewportHeight viewport height in pixels
     * @return a {@link Ray3} with origin and unit direction in world space
     * @throws IllegalArgumentException if viewport dimensions are not positive
     */
    public static Ray3 unproject(double sx, double sy,
                                 Matrix4f invVp, int viewportWidth, int viewportHeight) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("viewport must be > 0: " + viewportWidth + ", " + viewportHeight);
        }
        double ndcX = 2.0 * sx / viewportWidth - 1.0;
        double ndcY = 1.0 - 2.0 * sy / viewportHeight;

        Vector4f nearPoint = new Vector4f((float) ndcX, (float) ndcY, -1.0f, 1.0f);
        Vector4f farPoint = new Vector4f((float) ndcX, (float) ndcY, 1.0f, 1.0f);

        invVp.transform(nearPoint);
        invVp.transform(farPoint);

        double nx = nearPoint.x() / nearPoint.w();
        double ny = nearPoint.y() / nearPoint.w();
        double nz = nearPoint.z() / nearPoint.w();
        double fx = farPoint.x() / farPoint.w();
        double fy = farPoint.y() / farPoint.w();
        double fz = farPoint.z() / farPoint.w();

        double dx = fx - nx;
        double dy = fy - ny;
        double dz = fz - nz;
        double len = StrictMath.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0.0) {
            throw new IllegalStateException("Degenerate unprojection: ray length is zero");
        }
        return new Ray3(nx, ny, nz, dx / len, dy / len, dz / len);
    }

    /**
     * A 3D ray defined by an origin and a unit direction vector.
     *
     * @param ox origin x
     * @param oy origin y
     * @param oz origin z
     * @param dx direction x (unit)
     * @param dy direction y (unit)
     * @param dz direction z (unit)
     */
    public record Ray3(double ox, double oy, double oz, double dx, double dy, double dz) {

        /**
         * Tests intersection with a sphere.
         *
         * @param cx sphere center x
         * @param cy sphere center y
         * @param cz sphere center z
         * @param radius sphere radius
         * @return the positive distance along the ray to the intersection, or -1 if no hit
         */
        public double intersectSphere(double cx, double cy, double cz, double radius) {
            if (radius < 0) {
                throw new IllegalArgumentException("radius must be >= 0: " + radius);
            }
            double lx = cx - ox;
            double ly = cy - oy;
            double lz = cz - oz;
            double a = dx * dx + dy * dy + dz * dz;
            // Quadratic from |o + t*d - c|^2 = r^2 with l = c - o:
            //   a = d.d, b = -2 d.l, c = l.l - r^2
            // (the sign on b matters: a positive sign negates both roots)
            double b = -2.0 * (lx * dx + ly * dy + lz * dz);
            double c = lx * lx + ly * ly + lz * lz - radius * radius;
            double discriminant = b * b - 4.0 * a * c;
            if (discriminant < 0.0) {
                return -1.0;
            }
            double sqrtD = StrictMath.sqrt(discriminant);
            double t1 = (-b - sqrtD) / (2.0 * a);
            double t2 = (-b + sqrtD) / (2.0 * a);
            if (t1 > 0.0) return t1;
            if (t2 > 0.0) return t2;
            return -1.0;
        }
    }
}
