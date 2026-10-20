package ua.battlesim.gfx.units.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the procedural tank: hull, two tracks (with a scrolling tread),
 * four road wheels, a turret and a gun barrel, plus a blob shadow and a
 * selection marker.
 *
 * <p>Local frame: {@code +X} forward, {@code +Y} up, {@code +Z} right. All
 * geometry is static; the parts are animated at render time by
 * {@link UnitVertexWriter}.
 *
 * <p>Pure CPU, deterministic.
 */
public final class TankModel {

    /** Radial segments per wheel. */
    static final int SEG = 12;
    /** Wheel radius (metres) — matches {@link UnitMath#WHEEL_RADIUS}. */
    static final float WHEEL_R = (float) UnitMath.WHEEL_RADIUS;

    private TankModel() {
    }

    public static UnitModel build() {
        List<Piece> p = new ArrayList<>();

        // Hull (painted in the side colour).
        p.add(new Piece(Role.HULL, Geo.box(0.0, 1.0, 0.0, 2.6, 0.8, 1.8), 0, 0, 0));

        // Tracks (dark, scrolling tread) on either side.
        p.add(new Piece(Role.TRACK, Geo.box(0.0, 0.7, 1.15, 3.0, 0.9, 0.5), 0, 0, 0));
        p.add(new Piece(Role.TRACK, Geo.box(0.0, 0.7, -1.15, 3.0, 0.9, 0.5), 0, 0, 0));

        // Four road wheels (two per side), pivots at their centres.
        int[] xs = { -1, 1 };
        for (int side = -1; side <= 1; side += 2) {
            for (int x : xs) {
                float z = side * 1.15f;
                float[] wheel = Geo.cylinderZ(x, WHEEL_R, z, WHEEL_R, 0.28f, SEG);
                p.add(new Piece(Role.WHEEL, wheel, x, WHEEL_R, z));
            }
        }

        // Turret (rotates with the gun) and barrel (aims + recoils).
        float tx = 0.2f, ty = 1.65f;
        p.add(new Piece(Role.TURRET, Geo.box(tx, ty, 0.0, 1.2f, 0.5f, 1.2f), tx, ty, 0));
        p.add(new Piece(Role.GUN, Geo.box(1.4f, ty, 0.0, 1.3f, 0.16f, 0.16f), tx, ty, 0));

        // Blob shadow + selection marker (both on the ground).
        p.add(new Piece(Role.SHADOW, Geo.disc(0.0, 0.0, 1.7, 1.7, 16), 0, 0, 0));
        p.add(new Piece(Role.MARKER, Geo.ring(0.0, 0.0, 1.9, 2.15, 28), 0, 0, 0));

        return new UnitModel(0, p);
    }
}
