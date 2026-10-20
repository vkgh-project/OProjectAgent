package ua.battlesim.gfx.units.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the procedural APC with infantry: a hull, eight wheels (four per
 * side), a turret with a gun, three seated soldiers (torso, vest, head),
 * a blob shadow and a selection marker.
 *
 * <p>Local frame: {@code +X} forward, {@code +Y} up, {@code +Z} right.
 *
 * <p>Pure CPU, deterministic.
 */
public final class ApcModel {

    private ApcModel() {
    }

    public static UnitModel build() {
        List<Piece> p = new ArrayList<>();

        // Hull (painted in the side colour).
        p.add(new Piece(Role.HULL, Geo.box(0.0, 0.95, 0.0, 3.4, 1.1, 2.4), 0, 0, 0));

        // Eight wheels (four per side), pivots at their centres.
        float[] wx = { -1.7f, -0.7f, 0.7f, 1.7f };
        for (int side = -1; side <= 1; side += 2) {
            float z = side * 1.2f;
            for (int i = 0; i < 4; i++) {
                float x = wx[i];
                float[] wheel = Geo.cylinderZ(x, TankModel.WHEEL_R, z, TankModel.WHEEL_R, 0.25f, TankModel.SEG);
                p.add(new Piece(Role.WHEEL, wheel, x, TankModel.WHEEL_R, z));
            }
        }

        // Turret + gun.
        float ty = 1.7f;
        p.add(new Piece(Role.TURRET, Geo.box(0.0, ty, 0.0, 0.9f, 0.45f, 0.9f), 0, ty, 0));
        p.add(new Piece(Role.GUN, Geo.box(1.1f, ty, 0.0, 1.1f, 0.14f, 0.14f), 0, ty, 0));

        // Three seated soldiers in the rear hatch.
        float[] zs = { -0.6f, 0.0f, 0.6f };
        float sx = -1.2f;
        for (float z : zs) {
            p.add(new Piece(Role.TORSO, Geo.box(sx, 1.15f, z, 0.32f, 0.55f, 0.3f), sx, 1.15f, z));
            p.add(new Piece(Role.VEST, Geo.box(sx, 1.2f, z, 0.34f, 0.4f, 0.32f), sx, 1.2f, z));
            p.add(new Piece(Role.HEAD, Geo.box(sx, 1.55f, z, 0.26f, 0.26f, 0.26f), sx, 1.55f, z));
        }

        // Blob shadow + selection marker.
        p.add(new Piece(Role.SHADOW, Geo.disc(0.0, 0.0, 1.9, 1.9, 16), 0, 0, 0));
        p.add(new Piece(Role.MARKER, Geo.ring(0.0, 0.0, 2.0, 2.25, 28), 0, 0, 0));

        return new UnitModel(1, p);
    }
}
