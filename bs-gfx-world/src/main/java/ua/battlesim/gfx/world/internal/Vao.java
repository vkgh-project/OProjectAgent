package ua.battlesim.gfx.world.internal;

import java.nio.FloatBuffer;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.GL_FLOAT;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;
import static org.lwjgl.opengl.GL33.glDrawArraysInstanced;
import static org.lwjgl.opengl.GL33.glVertexAttribDivisor;

/**
 * A GPU vertex array object (VAO/VBO) with an arbitrary interleaved attribute
 * layout and optional per-instance data.
 *
 * <p>This is the OpenGL counterpart of {@link BuiltMesh}: it uploads the
 * vertex data and wires the vertex attribute pointers for a given layout.
 * Requires a current OpenGL context.
 *
 * <p>Base attributes are laid out sequentially in the vertex buffer starting
 * at byte offset 0 (attribute k's offset is the sum of the components of all
 * preceding attributes). When instance data is supplied it is stored in a
 * separate VBO and bound to {@code instanceLocation} with a divisor of 1.
 *
 * <p>Implements AutoCloseable for try-with-resources.
 */
public final class Vao implements AutoCloseable {

    /** A vertex attribute: its shader location and component count. */
    public record Attribute(int location, int components) {
        public Attribute {
            if (location < 0) {
                throw new IllegalArgumentException("location must be >= 0: " + location);
            }
            if (components < 1) {
                throw new IllegalArgumentException("components must be >= 1: " + components);
            }
        }
    }

    private final int vaoId;
    private final int vboId;
    private final int instanceVboId;
    private final int vertexCount;
    private final int instanceCount;
    private final boolean instanced;

    /**
     * Creates a non-instanced VAO from interleaved vertex data.
     *
     * @param vertices   interleaved float vertex data
     * @param stride     number of floats per vertex
     * @param attributes the vertex attributes, in layout order
     */
    public Vao(float[] vertices, int stride, List<Attribute> attributes) {
        this(vertices, stride, attributes, null, 1, List.of());
    }

    /**
     * Creates a VAO, optionally instanced.
     *
     * @param vertices        interleaved float vertex data (base geometry)
     * @param stride          number of floats per base vertex
     * @param attributes      the base vertex attributes, in layout order
     * @param instanceData    per-instance floats, or null for a non-instanced mesh
     * @param instanceStride  number of floats per instance (ignored when not instanced)
     * @param instanceAttributes the instance vertex attributes, in layout order
     */
    public Vao(float[] vertices, int stride, List<Attribute> attributes,
               float[] instanceData, int instanceStride, List<Attribute> instanceAttributes) {
        if (vertices == null) {
            throw new IllegalArgumentException("vertices must not be null");
        }
        if (stride <= 0) {
            throw new IllegalArgumentException("stride must be > 0: " + stride);
        }
        if (vertices.length % stride != 0) {
            throw new IllegalArgumentException("vertices length not a multiple of stride");
        }
        if (attributes == null || attributes.isEmpty()) {
            throw new IllegalArgumentException("attributes must not be empty");
        }
        this.instanced = instanceData != null;
        if (instanced) {
            if (instanceAttributes == null || instanceAttributes.isEmpty()) {
                throw new IllegalArgumentException("instanceAttributes must not be empty");
            }
            if (instanceStride < 1) {
                throw new IllegalArgumentException("instanceStride must be >= 1: " + instanceStride);
            }
            if (instanceData.length % instanceStride != 0) {
                throw new IllegalArgumentException("instance data not a multiple of instanceStride");
            }
            int totalComponents = 0;
            for (Attribute attribute : instanceAttributes) {
                totalComponents += attribute.components();
            }
            if (totalComponents != instanceStride) {
                throw new IllegalArgumentException(
                        "instance attribute components (" + totalComponents
                                + ") must equal instanceStride (" + instanceStride + ")");
            }
        }

        this.vertexCount = vertices.length / stride;
        this.instanceCount = instanced ? instanceData.length / instanceStride : 0;

        this.vaoId = glGenVertexArrays();
        this.vboId = glGenBuffers();
        this.instanceVboId = instanced ? glGenBuffers() : 0;

        glBindVertexArray(vaoId);

        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        FloatBuffer buf = org.lwjgl.BufferUtils.createFloatBuffer(vertices.length);
        buf.put(vertices).flip();
        glBufferData(GL_ARRAY_BUFFER, buf, GL_STATIC_DRAW);
        int byteStride = stride * 4;
        int offset = 0;
        for (Attribute attribute : attributes) {
            glEnableVertexAttribArray(attribute.location());
            glVertexAttribPointer(attribute.location(), attribute.components(), GL_FLOAT,
                    false, byteStride, offset);
            offset += attribute.components() * 4;
        }

        if (instanced) {
            glBindBuffer(GL_ARRAY_BUFFER, instanceVboId);
            FloatBuffer instBuf = org.lwjgl.BufferUtils.createFloatBuffer(instanceData.length);
            instBuf.put(instanceData).flip();
            glBufferData(GL_ARRAY_BUFFER, instBuf, GL_STATIC_DRAW);
            int ioffset = 0;
            for (Attribute ia : instanceAttributes) {
                glEnableVertexAttribArray(ia.location());
                glVertexAttribPointer(ia.location(), ia.components(), GL_FLOAT,
                        false, instanceStride * 4, ioffset);
                glVertexAttribDivisor(ia.location(), 1);
                ioffset += ia.components() * 4;
            }
        }

        glBindVertexArray(0);
    }

    /** @return the VAO id */
    public int vaoId() {
        return vaoId;
    }

    /** @return the number of base vertices */
    public int vertexCount() {
        return vertexCount;
    }

    /** @return the number of instances (0 when not instanced) */
    public int instanceCount() {
        return instanceCount;
    }

    /** @return true if this VAO carries per-instance data */
    public boolean instanced() {
        return instanced;
    }

    /** Draws all base vertices as triangles. */
    public void draw() {
        glBindVertexArray(vaoId);
        org.lwjgl.opengl.GL11.glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    /** Draws the base geometry {@code count} times, one instance each. */
    public void drawInstanced(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0: " + count);
        }
        glBindVertexArray(vaoId);
        glDrawArraysInstanced(GL_TRIANGLES, 0, vertexCount, count);
        glBindVertexArray(0);
    }

    /** Deletes the OpenGL resources. */
    @Override
    public void close() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
        if (instanceVboId != 0) {
            glDeleteBuffers(instanceVboId);
        }
    }
}