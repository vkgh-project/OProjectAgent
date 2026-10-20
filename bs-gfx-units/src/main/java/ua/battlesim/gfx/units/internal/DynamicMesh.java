package ua.battlesim.gfx.units.internal;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
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
 * A GPU dynamic vertex array for per-frame, per-vertex unit geometry:
 * {@code pos(3) + normal(3) + color(4)} per vertex.
 *
 * <p>The vertex data is generated on the CPU every frame (see
 * {@link UnitVertexWriter}) and uploaded with {@link #upload(float[])}.
 *
 * <p>Requires a current OpenGL context; unit tests never construct this class.
 *
 * <p>Implements AutoCloseable for try-with-resources.
 */
public final class DynamicMesh implements AutoCloseable {

    private final int vaoId;
    private final int vboId;
    private int vertexCount;

    public DynamicMesh() {
        this.vaoId = glGenVertexArrays();
        this.vboId = glGenBuffers();
        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        int stride = UnitVertexWriter.VERT_STRIDE * 4;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 12);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 24);
        glBindVertexArray(0);
        this.vertexCount = 0;
    }

    /** @return the number of vertices currently uploaded */
    public int vertexCount() {
        return vertexCount;
    }

    /** Replaces the vertex data (expects interleaved {@code 10} floats per vertex). */
    public void upload(float[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (data.length % UnitVertexWriter.VERT_STRIDE != 0) {
            throw new IllegalArgumentException("data length must be a multiple of " + UnitVertexWriter.VERT_STRIDE);
        }
        vertexCount = data.length / UnitVertexWriter.VERT_STRIDE;
        if (vertexCount == 0) {
            return;
        }
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        FloatBuffer buf = org.lwjgl.BufferUtils.createFloatBuffer(data.length);
        buf.put(data).flip();
        glBufferData(GL_ARRAY_BUFFER, buf, GL_DYNAMIC_DRAW);
    }

    /** Drops the current vertex count (draws nothing). */
    public void clear() {
        vertexCount = 0;
    }

    /** Draws the uploaded triangles. */
    public void draw() {
        if (vertexCount == 0) {
            return;
        }
        glBindVertexArray(vaoId);
        org.lwjgl.opengl.GL11.glDrawArrays(GL_TRIANGLES, 0, vertexCount);
        glBindVertexArray(0);
    }

    @Override
    public void close() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
    }
}
