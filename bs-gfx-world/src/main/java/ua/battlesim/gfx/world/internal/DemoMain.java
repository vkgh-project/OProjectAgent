package ua.battlesim.gfx.world.internal;

import ua.battlesim.api.Terrain;
import ua.battlesim.gfx.core.OrbitCamera;
import ua.battlesim.gfx.core.Window;
import ua.battlesim.gfx.world.LightingParams;
import ua.battlesim.gfx.world.WorldRenderer;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glViewport;

/**
 * Visual demo: renders a deterministic world with the {@link WorldRenderer}.
 *
 * <p>Controls: right-drag to rotate, left-drag to pan, scroll to zoom,
 * SPACE toggles a rainy/wet look, R resets the camera, ESC closes.
 *
 * <p>macOS: run with {@code -XstartOnFirstThread}.
 */
public final class DemoMain {

    private DemoMain() {
    }

    public static void main(String[] args) {
        Window window = new Window("Battle Sim World", 1280, 720, false);
        try {
            OrbitCamera camera = new OrbitCamera(
                    200.0f, 0.0f, 200.0f, 700.0f, 0.8f, (float) (StrictMath.PI / 3.2),
                    (float) (StrictMath.PI / 3.0), 16.0f / 9.0f, 0.1f, 8000.0f,
                    20.0f, 5000.0f, 0.05f, (float) (StrictMath.PI - 0.05f));

            Terrain terrain = new DemoTerrain();
            WorldRenderer renderer = new WorldRenderer(terrain, 128);

            final double[] weather = { 0.0, 0.0 }; // [0] wet, [1] puddles
            final boolean[] rainy = { false };
            double lastTime = glfwGetTime();
            float time = 0.0f;

            window.setScrollHandler((xOff, yOff) -> camera.zoom((float) yOff));
            window.setKeyHandler((key, scancode, action, mods) -> {
                if (action != GLFW_PRESS) {
                    return;
                }
                if (key == GLFW_KEY_R) {
                    camera.reset();
                } else if (key == GLFW_KEY_SPACE) {
                    rainy[0] = !rainy[0];
                    weather[0] = rainy[0] ? 0.8 : 0.0;
                    weather[1] = rainy[0] ? 0.7 : 0.0;
                }
            });

            glEnable(GL_DEPTH_TEST);
            while (!window.shouldClose()) {
                double now = glfwGetTime();
                float dt = (float) (now - lastTime);
                lastTime = now;
                time += dt;

                window.pollEvents();
                int w = window.framebufferWidth();
                int h = window.framebufferHeight();
                camera.setAspect(h > 0 ? (float) w / h : 1.0f);
                glViewport(0, 0, w, h);
                glClearColor(0.55f, 0.70f, 0.85f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                LightingParams lighting = LightingParams.defaults()
                        .withTime(time)
                        .withWeather(0.0, weather[0], weather[1]);
                renderer.render(camera, lighting);

                window.swapBuffers();
            }

            renderer.dispose();
        } finally {
            window.close();
            Window.shutdown();
        }
    }
}