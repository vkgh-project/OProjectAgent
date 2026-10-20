package ua.battlesim.gfx.units.internal;

import ua.battlesim.api.Bridge;
import ua.battlesim.api.LineOfSight;
import ua.battlesim.api.NavGrid;
import ua.battlesim.api.PathPlanner;
import ua.battlesim.api.Prop;
import ua.battlesim.api.RenderSnapshot;
import ua.battlesim.api.Road;
import ua.battlesim.api.Side;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.UnitView;
import ua.battlesim.api.WaterField;
import ua.battlesim.api.WeatherState;
import ua.battlesim.gfx.core.OrbitCamera;
import ua.battlesim.gfx.core.Window;
import ua.battlesim.gfx.units.UnitRenderer;

import java.util.List;
import java.util.Optional;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_R;
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
 * Visual demo: renders a small scene of tanks, an APC and an infantry squad on
 * a gently sloping terrain, animating the alpha interpolation and the unit
 * animations. Controls: right-drag to rotate, left-drag to pan, scroll to zoom,
 * R resets the camera, ESC closes.
 *
 * <p>macOS: run with {@code -XstartOnFirstThread}.
 */
public final class DemoMain {

    private DemoMain() {
    }

    public static void main(String[] args) {
        Window window = new Window("Battle Sim Units", 1280, 720, false);
        UnitRenderer renderer = null;
        try {
            UnitRenderer r = new UnitRenderer(new DemoTerrain());
            renderer = r;
            OrbitCamera camera = new OrbitCamera(
                    0.0f, 0.0f, 0.0f, 160.0f, 0.9f, (float) (StrictMath.PI / 3.4),
                    (float) (StrictMath.PI / 3.0), 16.0f / 9.0f, 0.1f, 8000.0f,
                    10.0f, 3000.0f, 0.05f, (float) (StrictMath.PI - 0.05f));

            window.setScrollHandler((xOff, yOff) -> camera.zoom((float) yOff));
            window.setKeyHandler((key, scancode, action, mods) -> {
                if (action == GLFW_PRESS && key == GLFW_KEY_R) {
                    camera.reset();
                }
            });

            glEnable(GL_DEPTH_TEST);
            double lastTime = glfwGetTime();
            while (!window.shouldClose()) {
                double now = glfwGetTime();
                double t = now * 0.25;
                lastTime = now;

                window.pollEvents();
                int w = window.framebufferWidth();
                int h = window.framebufferHeight();
                camera.setAspect(h > 0 ? (float) w / h : 1.0f);
                glViewport(0, 0, w, h);
                glClearColor(0.55f, 0.70f, 0.85f, 1.0f);
                glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

                double alpha = 0.5 - 0.5 * StrictMath.cos(StrictMath.PI * t);
                renderer.render(scene(t), alpha, camera, Optional.of(1));
                window.swapBuffers();
            }
        } finally {
            if (renderer != null) {
                renderer.dispose();
            }
            window.close();
            Window.shutdown();
        }
    }

    private static RenderSnapshot scene(double t) {
        double s = StrictMath.sin(t);
        UnitView tank = new UnitView(1, Side.BLUE, UnitType.TANK,
                5.0 + 3.0 * s, 5.0, 5.0 - 3.0 * s, 5.0,
                0.3 * s, -0.3 * s, 1.2, 1.2, 100, UnitState.ATTACKING, 20.0 + 4.0 * t, -1);
        UnitView apc = new UnitView(2, Side.RED, UnitType.APC_INFANTRY,
                -5.0, 6.0, -5.0, 6.0,
                StrictMath.PI, StrictMath.PI, StrictMath.PI, StrictMath.PI, 100, UnitState.MOVING, 15.0 + 3.0 * t, -1);
        UnitView infantry = new UnitView(3, Side.BLUE, UnitType.INFANTRY,
                4.0, -6.0, 4.0, -6.0,
                StrictMath.PI / 2, StrictMath.PI / 2, StrictMath.PI / 2, StrictMath.PI / 2, 60, UnitState.ATTACKING, 10.0 + 2.0 * t, -1);
        UnitView wreck = new UnitView(4, Side.RED, UnitType.TANK,
                -6.0, -5.0, -6.0, -5.0, 0.6, 0.6, 0.6, 0.6, 0, UnitState.DESTROYED, 12.0, 3);
        WeatherState weather = new WeatherState(0.0, 0.0, 0.0, 2.0, 0.0, 0.2, 0.0, 0.0, 0.0);
        return new RenderSnapshot(1, (int) (t * 10.0), t, List.of(tank, apc, infantry, wreck), weather);
    }

    /** A deterministic, gently sloping fake terrain for the demo. */
    public static final class DemoTerrain implements Terrain {
        @Override
        public double size() {
            return 40.0;
        }

        @Override
        public double heightAt(double x, double z) {
            return 0.15 * StrictMath.sin(0.25 * x) + 0.10 * StrictMath.cos(0.20 * z);
        }

        @Override
        public double[] sunDirection() {
            return new double[] { 0.45, 0.80, 0.35 };
        }

        @Override
        public double shadeAt(double x, double z) {
            return 1.0;
        }

        @Override
        public WaterField water() {
            return null;
        }

        @Override
        public NavGrid nav() {
            return null;
        }

        @Override
        public LineOfSight los() {
            return null;
        }

        @Override
        public PathPlanner planner() {
            return null;
        }

        @Override
        public List<Road> roads() {
            return List.of();
        }

        @Override
        public List<Bridge> bridges() {
            return List.of();
        }

        @Override
        public List<Prop> trees() {
            return List.of();
        }

        @Override
        public List<Prop> rocks() {
            return List.of();
        }
    }
}