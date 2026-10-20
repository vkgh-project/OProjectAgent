package ua.battlesim.gfx.core;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;
import static org.lwjgl.opengl.GL33.glDrawArraysInstanced;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * An instanced GPU mesh with per-instance data.
 *
 * <p>Vertex attribute 0 is the base position (2 or 3 components).
 * Vertex attribute 1 is the per-instance data (e.g. offset).
 *
 * <p>Implements AutoCloseable for try-with-resources.
 */
public final class InstancedMesh implements AutoCloseable {

    private final int vaoId;
    private final int vboId;
    private final int iboId;
    private final int vertexCount;
    private final int instanceCount;

    public InstancedMesh(float[] vertices, int components, float[] instanceData, int instanceStride) {
        if (vertices == null) throw new IllegalArgumentException("vertices must not be null");
        if (instanceData == null) throw new IllegalArgumentException("instanceData must not be null");
        if (components < 2 || components > 3)
            throw new IllegalArgumentException("components must be 2 or 3: " + components);
        if (instanceStride < 1)
            throw new IllegalArgumentException("instanceStride must be >= 1: " + instanceStride);
        if (instanceData.length % instanceStride != 0)
            throw new IllegalArgumentException("instance data not a multiple of instanceStride");

        this.vertexCount = vertices.length / components;
        this.instanceCount = instanceData.length / instanceStride;

        this.vaoId = glGenVertexArrays();
        this.vboId = glGenBuffers();
        this.iboId = glGenBuffers();

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

        glBindBuffer(GL_ARRAY_BUFFER, iboId);
        try (org.lwjgl.system.MemoryStack stack = stackPush()) {
            FloatBuffer buf = stack.mallocFloat(instanceData.length);
            buf.put(instanceData).flip();
            glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);
        }
        int instStride = instanceStride * 4;
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, instanceStride, GL_FLOAT, false, instStride, 0);
        glVertexAttribDivisor(1, 1);

        glBindVertexArray(0);
    }

    public int vaoId() { return vaoId; }
    public int vertexCount() { return vertexCount; }
    public int instanceCount() { return instanceCount; }

    public void drawInstanced(int count) {
        glBindVertexArray(vaoId);
        glDrawArraysInstanced(GL_TRIANGLES, 0, vertexCount, count);
        glBindVertexArray(0);
    }

    @Override
    public void close() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
        glDeleteBuffers(iboId);
    }
}
