package ua.battlesim.gfx.world.internal;

import ua.battlesim.api.Bridge;
import ua.battlesim.api.Vec2;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the bridge builder: 216 vertices per bridge (deck + 2 rails +
 * 3 pillars), and that a degenerate (zero-length) bridge stays finite.
 */
class BridgeMeshBuilderTest {

    @Test
    void expectedPerBridgeCount() {
        assertEquals(36, BridgeMeshBuilder.VERTS_PER_BOX);
        assertEquals(6, BridgeMeshBuilder.BOXES_PER_BRIDGE);
        assertEquals(216, BridgeMeshBuilder.VERTS_PER_BRIDGE);
    }

    @Test
    void oneBridge_producesExpectedVertexCount() {
        TestTerrain terrain = new TestTerrain();
        terrain.bridges = List.of(
                new Bridge(new Vec2(0.0, 0.0), new Vec2(100.0, 0.0), 2.0, 8.0));
        BuiltMesh mesh = BridgeMeshBuilder.build(terrain);
        assertEquals(216, mesh.vertexCount());
        assertEquals(6, mesh.stride());
    }

    @Test
    void twoBridges_sumVertexCounts() {
        TestTerrain terrain = new TestTerrain();
        terrain.bridges = List.of(
                new Bridge(new Vec2(0.0, 0.0), new Vec2(100.0, 0.0), 2.0, 8.0),
                new Bridge(new Vec2(200.0, 0.0), new Vec2(200.0, 100.0), 1.5, 6.0));
        BuiltMesh mesh = BridgeMeshBuilder.build(terrain);
        assertEquals(216 * 2, mesh.vertexCount());
    }

    @Test
    void noBridges_emptyMesh() {
        TestTerrain terrain = new TestTerrain();
        BuiltMesh mesh = BridgeMeshBuilder.build(terrain);
        assertEquals(0, mesh.vertexCount());
    }

    @Test
    void degenerateBridge_staysFinite() {
        TestTerrain terrain = new TestTerrain();
        terrain.bridges = List.of(
                new Bridge(new Vec2(50.0, 50.0), new Vec2(50.0, 50.0), 2.0, 8.0));
        BuiltMesh mesh = BridgeMeshBuilder.build(terrain);
        assertEquals(216, mesh.vertexCount());
        assertTrue(mesh.isFinite(), "degenerate bridge must not produce NaN/Infinity");
    }

    @Test
    void output_isFinite() {
        TestTerrain terrain = new TestTerrain();
        terrain.bridges = List.of(
                new Bridge(new Vec2(10.0, 20.0), new Vec2(120.0, 90.0), 3.0, 10.0));
        BuiltMesh mesh = BridgeMeshBuilder.build(terrain);
        assertTrue(mesh.isFinite(), "bridge mesh must not contain NaN/Infinity");
    }

    @Test
    void rejectsNullTerrain() {
        assertThrows(NullPointerException.class, () -> BridgeMeshBuilder.build(null));
    }
}