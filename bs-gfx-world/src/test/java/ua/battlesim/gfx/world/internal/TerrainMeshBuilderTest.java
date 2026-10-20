package ua.battlesim.gfx.world.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the terrain mesh builder produces the expected vertex count, the
 * expected stride, and no NaN/Infinity values.
 */
class TerrainMeshBuilderTest {

    private final TestTerrain terrain = new TestTerrain();

    @Test
    void singleCell_givesSixVertices() {
        BuiltMesh mesh = TerrainMeshBuilder.build(terrain, 1);
        assertEquals(6, mesh.vertexCount());
        assertEquals(9, mesh.stride());
        assertEquals(TerrainMeshBuilder.STRIDE, mesh.stride());
    }

    @Test
    void fourCells_givesExpectedVertexCount() {
        BuiltMesh mesh = TerrainMeshBuilder.build(terrain, 4);
        assertEquals(4 * 4 * 6, mesh.vertexCount());
    }

    @Test
    void eightCells_givesExpectedVertexCount() {
        BuiltMesh mesh = TerrainMeshBuilder.build(terrain, 8);
        assertEquals(8 * 8 * 6, mesh.vertexCount());
    }

    @Test
    void flatTerrain_hasUpwardNormals() {
        terrain.height = 0.0;
        BuiltMesh mesh = TerrainMeshBuilder.build(terrain, 2);
        // each vertex: pos(3), normal(3), attrs(3). First normal is at index 3.
        assertEquals(0.0f, mesh.vertices()[3], 1e-6f);
        assertEquals(1.0f, mesh.vertices()[4], 1e-6f);
        assertEquals(0.0f, mesh.vertices()[5], 1e-6f);
    }

    @Test
    void output_isFinite() {
        BuiltMesh mesh = TerrainMeshBuilder.build(terrain, 8);
        assertTrue(mesh.isFinite(), "terrain mesh must not contain NaN/Infinity");
    }

    @Test
    void rejectsNonPositiveCells() {
        assertThrows(IllegalArgumentException.class, () -> TerrainMeshBuilder.build(terrain, 0));
        assertThrows(IllegalArgumentException.class, () -> TerrainMeshBuilder.build(terrain, -3));
    }

    @Test
    void rejectsNullTerrain() {
        assertThrows(NullPointerException.class, () -> TerrainMeshBuilder.build(null, 4));
    }
}