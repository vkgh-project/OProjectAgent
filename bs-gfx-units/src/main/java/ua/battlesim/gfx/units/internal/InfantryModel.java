package ua.battlesim.gfx.units.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the procedural infantry squad of four soldiers (a 2x2 formation),
 * each with two walking legs, a torso, a vest and a head, plus a shared blob
 * shadow and a selection marker.
 *
 * <p>Local frame: {@code +X} forward, {@code +Y} up, {@code +Z} right. Each
 * leg pivots at its hip ({@code animSign} +1 for the left leg, -1 for the
 * right) so the walk cycle swings them alternately.
 *
 * <p>Pure CPU, deterministic.
 */
public final class InfantryModel {

    private InfantryModel() {
    }

    public static UnitModel build() {
        List<Piece> p = new ArrayList<>();

        float[] sx = { -0.5f, 0.5f };
        float[] sz = { -0.5f, 0.5f };
        float hipY = 0.95f;
        for (float x : sx) {
            for (float z : sz) {
                // Walking legs, pivoting at the hip.
                p.add(new Piece(Role.LEG, Geo.box(x, 0.5f, z - 0.12f, 0.16f, 0.9f, 0.16f), x, hipY, z - 0.12f, +1.0));
                p.add(new Piece(Role.LEG, Geo.box(x, 0.5f, z + 0.12f, 0.16f, 0.9f, 0.16f), x, hipY, z + 0.12f, -1.0));
                // Torso, vest and head.
                p.add(new Piece(Role.TORSO, Geo.box(x, 1.25f, z, 0.32f, 0.55f, 0.3f), x, 1.25f, z));
                p.add(new Piece(Role.VEST, Geo.box(x, 1.3f, z, 0.34f, 0.42f, 0.32f), x, 1.3f, z));
                p.add(new Piece(Role.HEAD, Geo.box(x, 1.68f, z, 0.26f, 0.28f, 0.26f), x, 1.68f, z));
            }
        }

        // Shared blob shadow + selection marker.
        p.add(new Piece(Role.SHADOW, Geo.disc(0.0, 0.0, 1.2, 1.2, 16), 0, 0, 0));
        p.add(new Piece(Role.MARKER, Geo.ring(0.0, 0.0, 1.35, 1.55, 24), 0, 0, 0));

        return new UnitModel(2, p);
    }
}
