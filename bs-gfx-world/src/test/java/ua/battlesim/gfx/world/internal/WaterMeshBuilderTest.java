package ua.battlesim.gfx.world.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the water mesh builder: a cell is emitted only when it has water,
 * the vertex count matches {@code 6 * wetCells}, and the output is finite.
 */
class WaterMeshBuilderTest {

    @Test
    void dryTerrain_producesNoWater() {
        TestTerrain terrain = new TestTerrain();
        terrain.waterMode = 0;
        BuiltMesh mesh = WaterMeshBuilder.build(terrain, 4);
        assertEquals(0, mesh.vertexCount());
        assertEquals(6, mesh.stride());
    }

    @Test
    void floodedTerrain_producesAllCells() {
        TestTerrain terrain = new TestTerrain();
        terrain.waterMode = 1;
        int cells = 4;
        BuiltMesh mesh = WaterMeshBuilder.build(terrain, cells);
        assertEquals(cells * cells * 6, mesh.vertexCount());
        assertEquals(WaterMeshBuilder.wetCellCount(terrain, cells), cells * cells);
    }

    @Test
    void halfFlooded_producesHalfTheCells() {
        TestTerrain terrain = new TestTerrain();
        terrain.waterMode = 2; // water only where x < size/2
        int cells = 4;
        BuiltMesh mesh = WaterMeshBuilder.build(terrain, cells);
        int expectedWet = WaterMeshBuilder.wetCellCount(terrain, cells);
        assertEquals(expectedWet * 6, mesh.vertexCount());
        assertEquals(8, expectedWet, "two wet columns x four rows for size=100, cells=4");
    }

    @Test
    void waterSurface_isAtWaterLevel() {
        TestTerrain terrain = new TestTerrain();
        terrain.waterMode = 1;
        BuiltMesh mesh = WaterMeshBuilder.build(terrain, 2);
        // first vertex y (index 1) is the water level
        assertEquals(WaterMeshBuilder.WATER_LEVEL, mesh.vertices()[1], 1e-6f);
    }

    @Test
    void output_isFinite() {
        TestTerrain terrain = new TestTerrain();
        terrain.waterMode = 1;
        BuiltMesh mesh = WaterMeshBuilder.build(terrain, 8);
        assertTrue(mesh.isFinite(), "water mesh must not contain NaN/Infinity");
    }

    @Test
    void rejectsNonPositiveCells() {
        TestTerrain terrain = new TestTerrain();
        assertThrows(IllegalArgumentException.class, () -> WaterMeshBuilder.build(terrain, 0));
    }

    @Test
    void rejectsNullTerrain() {
        assertThrows(NullPointerException.class, () -> WaterMeshBuilder.build(null, 4));
    }
}