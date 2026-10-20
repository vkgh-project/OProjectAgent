package ua.battlesim.gfx.fx.internal;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
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

/**
 * A GPU dynamic vertex array for solid unlit geometry (tracer quads).
 *
 * <p>Per vertex: {@code pos(3) + color(4)} (7 floats).
 *
 * <p>Requires a current OpenGL context; unit tests never construct this
 * class.
 *
 * <p>Implements AutoCloseable for try-with-resources.
 */
public final class SolidVao implements AutoCloseable {

    /** Number of floats per vertex. */
    public static final int FLOATS_PER_VERTEX = 7;

    private final int vaoId;
    private final int vboId;
    private int vertexCount;

    public SolidVao() {
        this.vaoId = glGenVertexArrays();
        this.vboId = glGenBuffers();
        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        int stride = FLOATS_PER_VERTEX * 4;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, stride, 12);
        glBindVertexArray(0);
        this.vertexCount = 0;
    }

    /**
     * Replaces the vertex data.
     *
     * @param data    interleaved float data, a multiple of {@code 7} floats
     * @param vertices the number of vertices ({@code >= 0})
     */
    public void upload(float[] data, int vertices) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        if (vertices < 0) {
            throw new IllegalArgumentException("vertices must be >= 0: " + vertices);
        }
        if (data.length != vertices * FLOATS_PER_VERTEX) {
            throw new IllegalArgumentException("data length must be " + vertices * FLOATS_PER_VERTEX
                    + " floats, got: " + data.length);
        }
        vertexCount = vertices;
        if (vertices == 0) {
            return;
        }
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        FloatBuffer buf = org.lwjgl.BufferUtils.createFloatBuffer(data.length);
        buf.put(data).flip();
        glBufferData(GL_ARRAY_BUFFER, buf, GL_DYNAMIC_DRAW);
    }

    /** @return the number of vertices currently uploaded */
    public int vertexCount() {
        return vertexCount;
    }

    /** Draws the uploaded triangles. */
    public void draw() {
        if (vertexCount == 0) {
            return;
        }
        glBindVertexArray(vaoId);
        org.lwjgl.opengl.GL11.glDrawArrays(org.lwjgl.opengl.GL11.GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    @Override
    public void close() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
    }
}