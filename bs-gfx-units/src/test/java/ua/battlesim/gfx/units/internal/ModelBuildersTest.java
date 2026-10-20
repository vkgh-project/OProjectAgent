package ua.battlesim.gfx.units.internal;

import ua.battlesim.api.UnitType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the procedural model builders: the requested piece structure,
 * deterministic vertex counts and NaN-free geometry for all three unit types.
 */
class ModelBuildersTest {

    @Test
    void tank_structure() {
        UnitModel m = TankModel.build();
        assertEquals(1, m.countRole(Role.HULL));
        assertEquals(2, m.countRole(Role.TRACK));
        assertEquals(4, m.countRole(Role.WHEEL));
        assertEquals(1, m.countRole(Role.TURRET));
        assertEquals(1, m.countRole(Role.GUN));
        assertEquals(1, m.countRole(Role.SHADOW));
        assertEquals(1, m.countRole(Role.MARKER));
        assertTrue(m.totalVertexCount() > 0);
    }

    @Test
    void apc_structure() {
        UnitModel m = ApcModel.build();
        assertEquals(1, m.countRole(Role.HULL));
        assertEquals(8, m.countRole(Role.WHEEL));
        assertEquals(1, m.countRole(Role.TURRET));
        assertEquals(1, m.countRole(Role.GUN));
        assertEquals(3, m.countRole(Role.TORSO));
        assertEquals(3, m.countRole(Role.VEST));
        assertEquals(3, m.countRole(Role.HEAD));
    }

    @Test
    void infantry_structure() {
        UnitModel m = InfantryModel.build();
        assertEquals(4, m.countRole(Role.TORSO));
        assertEquals(8, m.countRole(Role.LEG));
        assertEquals(4, m.countRole(Role.VEST));
        assertEquals(4, m.countRole(Role.HEAD));
    }

    @Test
    void dispatch_by_type() {
        assertSame(UnitModels.TANK, UnitModels.forType(UnitType.TANK));
        assertSame(UnitModels.APC, UnitModels.forType(UnitType.APC_INFANTRY));
        assertSame(UnitModels.INFANTRY, UnitModels.forType(UnitType.INFANTRY));
    }

    @Test
    void geometry_isFinite() {
        for (UnitModel m : new UnitModel[] { TankModel.build(), ApcModel.build(), InfantryModel.build() }) {
            for (Piece p : m.pieces()) {
                assertEquals(0, p.base.length % Piece.STRIDE);
                for (float v : p.base) {
                    assertTrue(Float.isFinite(v), "geometry must be finite");
                }
            }
        }
    }

    @Test
    void vertex_counts_areStable() {
        assertEquals(TankModel.build().totalVertexCount(), UnitModels.TANK.totalVertexCount());
        assertEquals(ApcModel.build().totalVertexCount(), UnitModels.APC.totalVertexCount());
        assertEquals(InfantryModel.build().totalVertexCount(), UnitModels.INFANTRY.totalVertexCount());
    }
}
