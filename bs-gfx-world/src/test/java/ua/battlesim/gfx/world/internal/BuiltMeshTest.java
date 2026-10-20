package ua.battlesim.gfx.world.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the {@link BuiltMesh} structural invariants and finiteness check.
 */
class BuiltMeshTest {

    @Test
    void vertexCount_dividesEvenly() {
        BuiltMesh mesh = new BuiltMesh(new float[] { 1, 2, 3, 4, 5, 6 }, 3);
        assertEquals(2, mesh.vertexCount());
    }

    @Test
    void emptyMesh_isValid() {
        BuiltMesh mesh = new BuiltMesh(new float[0], 9);
        assertEquals(0, mesh.vertexCount());
        assertTrue(mesh.isFinite());
    }

    @Test
    void isFinite_detectsNaN() {
        BuiltMesh mesh = new BuiltMesh(new float[] { 1.0f, Float.NaN, 3.0f }, 1);
        assertFalse(mesh.isFinite());
    }

    @Test
    void rejectsNonPositiveStride() {
        assertThrows(IllegalArgumentException.class, () -> new BuiltMesh(new float[] { 1, 2 }, 0));
    }

    @Test
    void rejectsLengthNotMultipleOfStride() {
        assertThrows(IllegalArgumentException.class, () -> new BuiltMesh(new float[] { 1, 2, 3 }, 2));
    }

    @Test
    void rejectsNullVertices() {
        assertThrows(NullPointerException.class, () -> new BuiltMesh(null, 3));
    }
}