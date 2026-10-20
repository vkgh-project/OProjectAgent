package ua.battlesim.gfx.world.internal;

import ua.battlesim.api.Prop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the instanced prop builders: fixed base geometry counts, per
 * instance data matching the prop count, and finite output.
 */
class PropMeshBuilderTest {

    private static Prop prop(int i) {
        return new Prop(10.0 + i, 20.0 + i, 1.0 + i * 0.1, i * 0.3, (i % 3) / 3.0, i % 4);
    }

    @Test
    void baseGeometryCounts() {
        assertEquals(72, PropMeshBuilder.TREE_BASE_VERTEX_COUNT);
        assertEquals(24, PropMeshBuilder.ROCK_BASE_VERTEX_COUNT);
        assertEquals(6, PropMeshBuilder.BASE_STRIDE);
        assertEquals(7, PropMeshBuilder.INSTANCE_STRIDE);
    }

    @Test
    void trees_instanceCountMatchesProps() {
        TestTerrain terrain = new TestTerrain();
        terrain.trees = List.of(prop(0), prop(1), prop(2));
        PropMeshBuilder.PropSet set = PropMeshBuilder.buildTrees(terrain);
        assertEquals(72, set.baseVertexCount());
        assertEquals(3, set.instanceCount());
        assertEquals(7, set.instanceData().length / set.instanceCount());
    }

    @Test
    void rocks_instanceCountMatchesProps() {
        TestTerrain terrain = new TestTerrain();
        terrain.rocks = List.of(prop(0), prop(1), prop(2), prop(3), prop(4));
        PropMeshBuilder.PropSet set = PropMeshBuilder.buildRocks(terrain);
        assertEquals(24, set.baseVertexCount());
        assertEquals(5, set.instanceCount());
    }

    @Test
    void emptyProps_stillHasBaseGeometry() {
        TestTerrain terrain = new TestTerrain();
        PropMeshBuilder.PropSet trees = PropMeshBuilder.buildTrees(terrain);
        assertEquals(72, trees.baseVertexCount());
        assertEquals(0, trees.instanceCount());
        PropMeshBuilder.PropSet rocks = PropMeshBuilder.buildRocks(terrain);
        assertEquals(24, rocks.baseVertexCount());
        assertEquals(0, rocks.instanceCount());
    }

    @Test
    void instances_arePlacedOnTerrain_andFinite() {
        TestTerrain terrain = new TestTerrain();
        terrain.height = 7.0;
        terrain.trees = List.of(prop(0), prop(1));
        PropMeshBuilder.PropSet set = PropMeshBuilder.buildTrees(terrain);
        assertTrue(set.isFinite(), "prop data must not contain NaN/Infinity");
        // first instance: x, y (=ground), z, scale, rotation, tint, kind
        assertEquals(7.0f, set.instanceData()[1], 1e-5f);
        assertEquals(10.0f, set.instanceData()[0], 1e-5f);
    }

    @Test
    void rejectsNullTerrain() {
        assertThrows(NullPointerException.class, () -> PropMeshBuilder.buildTrees(null));
        assertThrows(NullPointerException.class, () -> PropMeshBuilder.buildRocks(null));
    }
}