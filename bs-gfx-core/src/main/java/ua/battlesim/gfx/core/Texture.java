package ua.battlesim.gfx.core;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * A GPU 2D texture.
 *
 * <p>Implements AutoCloseable for try-with-resources.
 */
public final class Texture implements AutoCloseable {

    private final int id;

    public Texture(int width, int height, int format, byte[] data) {
        if (width <= 0 || height <= 0)
            throw new IllegalArgumentException("width and height must be > 0: " + width + ", " + height);
        if (data == null) throw new IllegalArgumentException("data must not be null");

        this.id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        try (org.lwjgl.system.MemoryStack stack = stackPush()) {
            ByteBuffer buf = stack.malloc(data.length);
            buf.put(data).flip();
            glTexImage2D(GL_TEXTURE_2D, 0, format, width, height, 0, format, GL_UNSIGNED_BYTE, buf);
        }
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public int id() { return id; }
    public void bind() { glBindTexture(GL_TEXTURE_2D, id); }
    public void unbind() { glBindTexture(GL_TEXTURE_2D, 0); }

    public void setWrapping(int mode) {
        bind();
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, mode);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, mode);
        unbind();
    }

    public void setFiltering(int minFilter, int magFilter) {
        bind();
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, magFilter);
        unbind();
    }

    @Override
    public void close() { glDeleteTextures(id); }
}
