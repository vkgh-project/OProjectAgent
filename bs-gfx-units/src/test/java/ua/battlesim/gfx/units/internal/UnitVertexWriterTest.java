package ua.battlesim.gfx.units.internal;

import ua.battlesim.api.Side;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.UnitView;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the CPU vertex writer: it emits exactly the expected vertex count,
 * splits the shadow/marker geometry, only draws the selection marker when the
 * unit is selected, and never produces NaN/Infinity.
 */
class UnitVertexWriterTest {

    private static UnitView unit(Side side, UnitType type, UnitState state) {
        return new UnitView(1, side, type, 5.0, 0.0, 5.0, 0.0, 0.3, 0.3, 1.2, 1.2,
                100, state, 10.0, -1);
    }

    @Test
    void emits_expectedOpaqueAndShadowVertexCounts() {
        TestTerrain t = new TestTerrain();
        UnitPose pose = UnitPose.from(unit(Side.BLUE, UnitType.TANK, UnitState.MOVING), 0, 1.0, t, Optional.empty());
        FloatList opaque = new FloatList(64);
        FloatList shadow = new FloatList(64);
        UnitVertexWriter.write(UnitModels.TANK, pose, opaque, shadow);

        int expectedOpaque = 0;
        for (Piece p : UnitModels.TANK.pieces()) {
            if (p.role != Role.SHADOW && p.role != Role.MARKER) {
                expectedOpaque += p.vertexCount();
            }
        }
        int expectedShadow = 0;
        for (Piece p : UnitModels.TANK.pieces()) {
            if (p.role == Role.SHADOW) {
                expectedShadow += p.vertexCount();
            }
        }
        assertEquals(expectedOpaque, opaque.size() / UnitVertexWriter.VERT_STRIDE);
        assertEquals(expectedShadow, shadow.size() / UnitVertexWriter.VERT_STRIDE);
    }

    @Test
    void marker_onlyDrawnWhenSelected() {
        TestTerrain t = new TestTerrain();
        UnitView u = unit(Side.BLUE, UnitType.TANK, UnitState.READY);
        UnitPose sel = UnitPose.from(u, 0, 1.0, t, Optional.of(1));
        UnitPose noSel = UnitPose.from(u, 0, 1.0, t, Optional.empty());

        FloatList oSel = new FloatList(64);
        FloatList sSel = new FloatList(64);
        FloatList oNo = new FloatList(64);
        FloatList sNo = new FloatList(64);
        UnitVertexWriter.write(UnitModels.TANK, sel, oSel, sSel);
        UnitVertexWriter.write(UnitModels.TANK, noSel, oNo, sNo);

        assertTrue(sSel.size() > sNo.size(), "selected unit should add the marker vertices");
        assertEquals(oNo.size(), oSel.size(), "opaque geometry is unaffected by selection");
    }

    @Test
    void output_isFinite_for_allTypes() {
        TestTerrain t = new TestTerrain();
        t.slopeX = 0.2;
        t.slopeZ = -0.1;
        for (UnitType type : UnitType.values()) {
            UnitPose pose = UnitPose.from(unit(Side.RED, type, UnitState.ATTACKING), 3, 0.5, t, Optional.of(1));
            FloatList opaque = new FloatList(64);
            FloatList shadow = new FloatList(64);
            UnitVertexWriter.write(UnitModels.forType(type), pose, opaque, shadow);
            for (float v : opaque.toFloat()) {
                assertTrue(Float.isFinite(v), "opaque vertex data must be finite");
            }
            for (float v : shadow.toFloat()) {
                assertTrue(Float.isFinite(v), "shadow vertex data must be finite");
            }
        }
    }

    @Test
    void destroyedModel_staysFinite() {
        TestTerrain t = new TestTerrain();
        UnitPose pose = UnitPose.from(unit(Side.RED, UnitType.TANK, UnitState.DESTROYED), 0, 1.0, t, Optional.empty());
        FloatList opaque = new FloatList(64);
        FloatList shadow = new FloatList(64);
        UnitVertexWriter.write(UnitModels.TANK, pose, opaque, shadow);
        assertTrue(opaque.size() > 0);
        for (float v : opaque.toFloat()) {
            assertTrue(Float.isFinite(v));
        }
    }
}
