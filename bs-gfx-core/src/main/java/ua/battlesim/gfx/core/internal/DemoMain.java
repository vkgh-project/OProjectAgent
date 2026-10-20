package ua.battlesim.gfx.core.internal;

import ua.battlesim.gfx.core.Mesh;
import ua.battlesim.gfx.core.OrbitCamera;
import ua.battlesim.gfx.core.ShaderProgram;
import ua.battlesim.gfx.core.Window;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Demo: opens a window and draws a rotating triangle.
 *
 * <p>On macOS, run with:
 * <pre>java -XstartOnFirstThread ua.battlesim.gfx.core.internal.DemoMain</pre>
 *
 * <p>Controls:
 * <ul>
 *   <li>Left mouse drag &ndash; pan</li>
 *   <li>Right mouse drag &ndash; rotate</li>
 *   <li>Scroll wheel &ndash; zoom</li>
 *   <li>F11 &ndash; toggle fullscreen</li>
 *   <li>ESC &ndash; close</li>
 * </ul>
 */
public final class DemoMain {

    private static final String VERT_SRC = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            uniform mat4 uViewProj;
            uniform float uAngle;
            void main() {
                float c = cos(uAngle);
                float s = sin(uAngle);
                vec2 rotated = vec2(c * aPos.x - s * aPos.y, s * aPos.x + c * aPos.y);
                gl_Position = uViewProj * vec4(rotated, 0.0, 1.0);
            }
            """;

    private static final String FRAG_SRC = """
            #version 330 core
            out vec4 FragColor;
            void main() {
                FragColor = vec4(0.2, 0.6, 1.0, 1.0);
            }
            """;

    private DemoMain() {}

    public static void main(String[] args) {
        Window window = new Window("Battle Sim Demo", 1280, 720);
        try {
            OrbitCamera camera = new OrbitCamera(0, 0, 0, 3.0f, 0, (float) (StrictMath.PI / 2.5),
                    (float) (StrictMath.PI / 3.0), 16.0f / 9.0f, 0.01f, 100.0f,
                    0.5f, 50.0f, 0.05f, (float) (StrictMath.PI - 0.05f));

            ShaderProgram shader = new ShaderProgram(VERT_SRC, FRAG_SRC);

            float[] vertices = {
                    0.0f, -0.5f,
                    0.5f, 0.5f,
                    -0.5f, 0.5f
            };
            Mesh mesh = new Mesh(vertices, 2);

            window.setMouseButtonHandler((button, action, mods) -> {
                // Pan with left, rotate with right
            });
            window.setMouseMoveHandler((x, y) -> {
                // Handled by GLFW drag callbacks
            });
            window.setScrollHandler((xOff, yOff) -> {
                camera.zoom((float) yOff);
            });
            window.setKeyHandler((key, scancode, action, mods) -> {
                // Extra key handling
            });

            glEnable(GL_DEPTH_TEST);

            double lastTime = glfwGetTime();
            float angle = 0.0f;

            while (!window.shouldClose()) {
                double currentTime = glfwGetTime();
                float dt = (float) (currentTime - lastTime);
                lastTime = currentTime;
                angle += dt * 1.5f;

                window.pollEvents();

                int w = window.framebufferWidth();
                int h = window.framebufferHeight();
                camera.setAspect(h > 0 ? (float) w / h : 1.0f);

                glViewport(0, 0, w, h);
                glClearColor(0.1f, 0.1f, 0.15f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                shader.use();
                shader.setMat4("uViewProj", camera.viewProjectionMatrix());
                shader.setFloat("uAngle", angle);
                mesh.draw();
                ShaderProgram.unuse();

                window.swapBuffers();
            }

            mesh.close();
            shader.close();
        } finally {
            window.close();
            Window.shutdown();
        }
    }
}
