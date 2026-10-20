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
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * A GPU dynamic vertex array for billboarded effect particles.
 *
 * <p>One vertex per particle with {@code pos(3) + size(2) + color(4)}
 * (9 floats); the vertex shader expands each vertex into a camera-facing
 * quad (4 vertices per instance via {@code gl_VertexID}), so drawing is
 * {@code glDrawArraysInstanced(GL_TRIANGLES, 0, 4, particleCount)}.
 *
 * <p>The data is generated on the CPU every frame (see
 * {@link ua.battlesim.gfx.fx.EffectsRenderer}) and uploaded with
 * {@link #upload(float[], int)}.
 *
 * <p>Requires a current OpenGL context; unit tests never construct this
 * class.
 *
 * <p>Implements AutoCloseable for try-with-resources.
 */
public final class ParticleVao implements AutoCloseable {

    /** Number of floats per particle. */
    public static final int FLOATS_PER_PARTICLE = 9;

    private final int vaoId;
    private final int vboId;
    private int particleCount;

    public ParticleVao() {
        this.vaoId = glGenVertexArrays();
        this.vboId = glGenBuffers();
        glBindVertexArray(vaoId);
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        int stride = FLOATS_PER_PARTICLE * 4;
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 12);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 20);
        glBindVertexArray(0);
        this.particleCount = 0;
    }

    /**
     * Replaces the particle data.
     *
     * @param data      interleaved float data, {@code particleCount * 9} floats
     * @param particles the number of particles ({@code >= 0})
     */
    public void upload(float[] data, int particles) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        if (particles < 0) {
            throw new IllegalArgumentException("particles must be >= 0: " + particles);
        }
        if (data.length != particles * FLOATS_PER_PARTICLE) {
            throw new IllegalArgumentException("data length must be " + particles * FLOATS_PER_PARTICLE
                    + " floats, got: " + data.length);
        }
        particleCount = particles;
        if (particles == 0) {
            return;
        }
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        try (org.lwjgl.system.MemoryStack stack = stackPush()) {
            FloatBuffer buf = stack.mallocFloat(data.length);
            buf.put(data).flip();
            glBufferData(GL_ARRAY_BUFFER, buf, GL_DYNAMIC_DRAW);
        }
    }

    /** @return the number of particles currently uploaded */
    public int particleCount() {
        return particleCount;
    }

    /** Draws every uploaded particle as a camera-facing quad. */
    public void draw() {
        if (particleCount == 0) {
            return;
        }
        glBindVertexArray(vaoId);
        org.lwjgl.opengl.GL33.glDrawArraysInstanced(org.lwjgl.opengl.GL11.GL_TRIANGLES, 0, 4, particleCount);
        glBindVertexArray(0);
    }

    @Override
    public void close() {
        glDeleteVertexArrays(vaoId);
        glDeleteBuffers(vboId);
    }
}