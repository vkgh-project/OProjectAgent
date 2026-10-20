package ua.battlesim.gfx.world.internal;

import ua.battlesim.api.Road;
import ua.battlesim.api.Vec2;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the road ribbon builder: 5 vertices across, vertex count
 * {@code (P-1) * 4 * 6} per road, and finite output.
 */
class RoadMeshBuilderTest {

    private static Road road(int points) {
        Vec2[] v = new Vec2[points];
        for (int i = 0; i < points; i++) {
            v[i] = new Vec2(i * 30.0, (i % 2) * 20.0);
        }
        boolean[] bridge = new boolean[points];
        return new Road(List.of(v), 4.0, 0, bridge);
    }

    @Test
    void vertexCountFormula() {
        assertEquals(0, RoadMeshBuilder.roadVertexCount(1));
        assertEquals(24, RoadMeshBuilder.roadVertexCount(2));
        assertEquals(48, RoadMeshBuilder.roadVertexCount(3));
        assertEquals(96, RoadMeshBuilder.roadVertexCount(5));
    }

    @Test
    void fiveVerticesAcross_isExposed() {
        assertEquals(5, RoadMeshBuilder.ACROSS);
        assertEquals(6, RoadMeshBuilder.STRIDE);
    }

    @Test
    void singleRoad_vertexCount() {
        TestTerrain terrain = new TestTerrain();
        terrain.roads = List.of(road(3));
        BuiltMesh mesh = RoadMeshBuilder.build(terrain);
        assertEquals(48, mesh.vertexCount());
        assertEquals(6, mesh.stride());
    }

    @Test
    void multipleRoads_sumVertexCounts() {
        TestTerrain terrain = new TestTerrain();
        terrain.roads = List.of(road(3), road(5));
        BuiltMesh mesh = RoadMeshBuilder.build(terrain);
        assertEquals(48 + 96, mesh.vertexCount());
    }

    @Test
    void noRoads_emptyMesh() {
        TestTerrain terrain = new TestTerrain();
        BuiltMesh mesh = RoadMeshBuilder.build(terrain);
        assertEquals(0, mesh.vertexCount());
    }

    @Test
    void output_isFinite_andDrapedOnTerrain() {
        TestTerrain terrain = new TestTerrain();
        terrain.height = 5.0;
        terrain.roads = List.of(road(4));
        BuiltMesh mesh = RoadMeshBuilder.build(terrain);
        assertTrue(mesh.isFinite(), "road mesh must not contain NaN/Infinity");
        // ribbon is lifted above the terrain: y = height + LIFT
        assertEquals(5.0 + RoadMeshBuilder.LIFT, mesh.vertices()[1], 1e-4f);
    }

    @Test
    void rejectsNullTerrain() {
        assertThrows(NullPointerException.class, () -> RoadMeshBuilder.build(null));
    }
}