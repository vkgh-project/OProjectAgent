package ua.battlesim.gfx.world;

import ua.battlesim.api.Bridge;
import ua.battlesim.api.Prop;
import ua.battlesim.api.Road;
import ua.battlesim.api.Vec2;
import ua.battlesim.gfx.world.internal.TestTerrain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link WorldRenderer} constructs the expected CPU geometry without
 * a GPU and that {@code dispose} is safe to call.
 */
class WorldRendererTest {

    private static TestTerrain sampleWorld() {
        TestTerrain t = new TestTerrain();
        t.size = 200.0;
        t.waterMode = 2;
        t.roads = List.of(new Road(
                List.of(new Vec2(0.0, 100.0), new Vec2(100.0, 100.0), new Vec2(200.0, 100.0)),
                5.0, 0, new boolean[] { false, false, false }));
        t.bridges = List.of(new Bridge(new Vec2(80.0, 100.0), new Vec2(120.0, 100.0), 2.0, 6.0));
        t.trees = List.of(new Prop(10.0, 10.0, 1.0, 0.0, 0.5, 1), new Prop(50.0, 50.0, 1.2, 0.5, 0.2, 2));
        t.rocks = List.of(new Prop(20.0, 30.0, 0.8, 0.3, 0.9, 0));
        return t;
    }

    @Test
    void constructs_expectedGeometry_withoutGpu() {
        TestTerrain t = sampleWorld();
        int cells = 4;
        WorldRenderer renderer = new WorldRenderer(t, cells);
        try {
            assertEquals(cells * cells * 6, renderer.terrainGeometry().vertexCount());
            assertEquals(9, renderer.terrainGeometry().stride());
            assertTrue(renderer.terrainGeometry().isFinite());

            // water: two wet columns x four rows = 8 cells
            assertEquals(8 * 6, renderer.waterGeometry().vertexCount());
            assertTrue(renderer.waterGeometry().isFinite());

            assertEquals(48, renderer.roadGeometry().vertexCount());
            assertTrue(renderer.roadGeometry().isFinite());

            assertEquals(216, renderer.bridgeGeometry().vertexCount());
            assertTrue(renderer.bridgeGeometry().isFinite());

            assertEquals(72, renderer.trees().baseVertexCount());
            assertEquals(2, renderer.trees().instanceCount());
            assertTrue(renderer.trees().isFinite());

            assertEquals(24, renderer.rocks().baseVertexCount());
            assertEquals(1, renderer.rocks().instanceCount());
            assertTrue(renderer.rocks().isFinite());
        } finally {
            renderer.dispose();
        }
    }

    @Test
    void defaultConstructor_usesDefaultCells() {
        WorldRenderer renderer = new WorldRenderer(sampleWorld());
        try {
            assertEquals(WorldRenderer.DEFAULT_CELLS, renderer.gridCells());
            assertEquals(WorldRenderer.DEFAULT_CELLS * WorldRenderer.DEFAULT_CELLS * 6,
                    renderer.terrainGeometry().vertexCount());
        } finally {
            renderer.dispose();
        }
    }

    @Test
    void dispose_isIdempotent_andSafeWithoutRender() {
        WorldRenderer renderer = new WorldRenderer(sampleWorld());
        renderer.dispose();
        renderer.dispose(); // must not throw
    }

    @Test
    void close_delegatesToDispose() {
        WorldRenderer renderer = new WorldRenderer(sampleWorld());
        renderer.close();
        renderer.dispose(); // still safe
    }

    @Test
    void rejectsNullTerrain() {
        assertThrows(NullPointerException.class, () -> new WorldRenderer(null));
        assertThrows(NullPointerException.class, () -> new WorldRenderer(null, 4));
    }

    @Test
    void rejectsNonPositiveGridCells() {
        assertThrows(IllegalArgumentException.class, () -> new WorldRenderer(sampleWorld(), 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldRenderer(sampleWorld(), -1));
    }
}