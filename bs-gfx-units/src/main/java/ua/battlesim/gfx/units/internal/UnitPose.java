package ua.battlesim.gfx.units.internal;

import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitView;

import java.util.Objects;
import java.util.Optional;

/**
 * The per-unit animation state for one rendered frame, computed from a
 * {@link UnitView}, the interpolation {@code alpha} and the terrain.
 *
 * <p>Everything here is a deterministic function of those inputs (see
 * {@link UnitMath}), so it can be unit-tested without a GPU. The render loop
 * turns a pose into world-space vertices with {@link UnitVertexWriter}.
 *
 * <p>Immutable; safe to build on the render thread.
 */
public record UnitPose(
        double x, double y, double z,
        double yaw,
        double nx, double ny, double nz,
        double wheelAngle,
        double treadScroll,
        double gunAngle,
        double recoil,
        double legPhase,
        boolean charred,
        boolean lying,
        boolean selected,
        float[] paint) {

    public UnitPose {
        paint = Objects.requireNonNull(paint, "paint");
    }

    /**
     * Computes a unit's pose from a snapshot view.
     *
     * @param u          the unit view (current + previous frame values)
     * @param tick       the snapshot tick (drives the deterministic recoil pulse)
     * @param alpha      interpolation in {@code [0, 1]} between previous and current frame
     * @param terrain    the terrain (height + normal)
     * @param selectedId the currently selected unit id, if any
     */
    public static UnitPose from(UnitView u, int tick, double alpha, Terrain terrain, Optional<Integer> selectedId) {
        double t = UnitMath.clamp01(alpha);
        double x = UnitMath.lerp(u.prevX(), u.x(), t);
        double z = UnitMath.lerp(u.prevZ(), u.z(), t);
        double yaw = UnitMath.lerpAngle(u.prevHeading(), u.heading(), t);
        double y = terrain.heightAt(x, z);
        double[] n = UnitMath.normalAt(terrain, x, z);
        double aim = UnitMath.lerpAngle(u.prevAim(), u.aim(), t);

        double wheelAngle = UnitMath.wheelAngle(u.odometer());
        double treadScroll = wheelAngle / (2.0 * StrictMath.PI);
        double gunAngle = UnitMath.gunAngle(u.state(), yaw, aim);
        double recoil = UnitMath.recoil(tick, u.id(), u.state());
        double legPhase = UnitMath.legPhase(u.odometer());

        boolean destroyed = u.state() == UnitState.DESTROYED;
        boolean selected = selectedId.isPresent() && selectedId.get() == u.id();
        float[] paint = Paint.side(u.side());

        return new UnitPose(x, y, z, yaw, n[0], n[1], n[2],
                wheelAngle, treadScroll, gunAngle, recoil, legPhase,
                destroyed, destroyed, selected, paint);
    }
}
