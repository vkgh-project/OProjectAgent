package ua.battlesim.gfx.core;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * A GPU mesh with vertex data in a VBO and a VAO.
 *
 * <p>Vertex attribute 0 is the position (2 or 3 components).
 *
 * <p>Implements AutoCloseable for try-with-resources.
 */
public final class Mesh implements AutoCloseable {

    private final int vaoId;
    private final int vboId;
    private final int vertexCount;

    /**
     * Creates a new mesh from interleaved vertex data.
     *
     * @param vertices   interleaved float vertex data
     * @param components number of floats per vertex (2 or 3)
     */
    public Mesh(float[] vertices, int components) {
        if (vertices == null) throw new IllegalArgumentException("vertices must not be null");
        if (components < 2 || components > 3)
            throw new IllegalArgumentException("components must be 2 or 3: " + components);
        if (vertices.length % components != 0)
            throw new IllegalArgumentException("vertex count not a multiple of components");

        this.vertexCount = vertices.length / components;
        this.vaoId = glGenVertexArrays();
        this.vboId = glGenBuffers();

        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        try (org.lwjgl.system.MemoryStack stack = stackPush()) {
            FloatBuffer buf = stack.mallocFloat(vertices.length);
            buf.put(vertices).flip();
            glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);
        }

        int stride = components * 4;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, components, GL_FLOAT, false, stride, 0);

        glBindVertexArray(0);
    }

    /** @return the VAO id */
    public int vaoId() { return vaoId; }

    /** @return the number of vertices */
    public int vertexCount() { return vertexCount; }

    /** Draws the mesh as triangles. */
    public void draw() {
        glBindVertexArray(vaoId);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    /** @return the VBO id */
    public int vboId() { return vboId; }

    /** Deletes the OpenGL resources. */
    @Override
    public void close() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
    }
}
