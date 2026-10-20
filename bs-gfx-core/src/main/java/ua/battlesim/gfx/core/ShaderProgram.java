package ua.battlesim.gfx.core;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Compiles and links a vertex + fragment shader pair, and provides
 * a uniform location cache with clear error messages.
 *
 * <p>Implements AutoCloseable for try-with-resources.
 */
public final class ShaderProgram implements AutoCloseable {

    private final int programId;
    private final Map<String, Integer> uniformCache = new HashMap<>();

    public ShaderProgram(String vertexSource, String fragmentSource) {
        if (vertexSource == null) throw new IllegalArgumentException("vertexSource must not be null");
        if (fragmentSource == null) throw new IllegalArgumentException("fragmentSource must not be null");

        int vs = compileShader(GL_VERTEX_SHADER, vertexSource);
        int fs = compileShader(GL_FRAGMENT_SHADER, fragmentSource);

        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);

        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(prog);
            glDeleteProgram(prog);
            glDeleteShader(vs);
            glDeleteShader(fs);
            throw new IllegalStateException("Shader link failed:\n" + log);
        }

        glDeleteShader(vs);
        glDeleteShader(fs);
        this.programId = prog;
    }

    public int id() { return programId; }
    public void use() { glUseProgram(programId); }
    public static void unuse() { glUseProgram(0); }

    public int uniform(String name) {
        return uniformCache.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }

    public void setFloat(String name, float value) {
        int loc = uniform(name);
        if (loc >= 0) glUniform1f(loc, value);
    }

    public void setVec3(String name, Vector3f v) {
        int loc = uniform(name);
        if (loc >= 0) glUniform3f(loc, v.x(), v.y(), v.z());
    }

    public void setMat4(String name, Matrix4f matrix) {
        int loc = uniform(name);
        if (loc >= 0) {
            try (org.lwjgl.system.MemoryStack stack = stackPush()) {
                FloatBuffer buf = stack.mallocFloat(16);
                matrix.get(buf);
                glUniformMatrix4fv(loc, false, buf);
            }
        }
    }

    @Override
    public void close() { glDeleteProgram(programId); }

    private static int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            String typeStr = (type == GL_VERTEX_SHADER) ? "vertex" : "fragment";
            throw new IllegalStateException(typeStr + " shader compilation failed:\n" + log);
        }
        return shader;
    }
}
