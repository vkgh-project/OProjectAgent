package ua.battlesim.gfx.world.internal;

import java.util.Objects;

/**
 * Immutable, CPU-side geometry produced by a mesh builder: an interleaved
 * float array plus the number of floats per vertex (the stride).
 *
 * <p>This is deliberately independent of OpenGL so the mesh builders can be
 * unit-tested without a GPU or a rendering context. The GPU upload (VAO/VBO)
 * is done separately by {@link Vao}.
 *
 * <p>Vertices are triangle-soup (the {@link Vao} draws them with
 * {@code GL_TRIANGLES}).
 *
 * <p>Immutable and thread-safe; the backing array is never mutated after
 * construction.
 */
public record BuiltMesh(float[] vertices, int stride) {

    public BuiltMesh {
        Objects.requireNonNull(vertices, "vertices");
        if (stride <= 0) {
            throw new IllegalArgumentException("stride must be > 0: " + stride);
        }
        if (vertices.length % stride != 0) {
            throw new IllegalArgumentException(
                    "vertices length " + vertices.length + " must be a multiple of stride " + stride);
        }
    }

    /** @return the number of vertices (triangle-soup, so already expanded) */
    public int vertexCount() {
        return vertices.length / stride;
    }

    /** @return true if every float component is finite (no NaN or infinity) */
    public boolean isFinite() {
        for (float v : vertices) {
            if (!Float.isFinite(v)) {
                return false;
            }
        }
        return true;
    }
}