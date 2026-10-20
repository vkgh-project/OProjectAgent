package ua.battlesim.app.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.joml.Vector3f;

import ua.battlesim.api.RenderSnapshot;
import ua.battlesim.api.SessionView;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.UnitView;
import ua.battlesim.api.WeatherState;
import ua.battlesim.gfx.core.InputRouter;
import ua.battlesim.gfx.core.OrbitCamera;
import ua.battlesim.gfx.core.UiCommand;
import ua.battlesim.gfx.core.Window;
import ua.battlesim.gfx.fx.EffectsRenderer;
import ua.battlesim.gfx.fx.SkyRenderer;
import ua.battlesim.gfx.units.UnitRenderer;
import ua.battlesim.gfx.world.LightingParams;
import ua.battlesim.gfx.world.Vec3;
import ua.battlesim.gfx.world.WorldRenderer;
import ua.battlesim.session.DefaultSession;
import ua.battlesim.session.SessionStats;
import ua.battlesim.ui.HudModel;
import ua.battlesim.ui.HudRenderer;
import ua.battlesim.ui.Picker;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glViewport;

/**
 * The windowed game: the main loop of the presentation layer.
 *
 * <p>Per frame it (1) polls the window input and routes it through the
 * {@link InputRouter} into {@link UiCommand}s, (2) translates those commands
 * into session calls (pause/speed) and camera/window/picker actions, (3)
 * advances the session by the elapsed wall time, (4) feeds the drained events
 * to the effects, and (5) renders sky, world, units, effects and the HUD.
 *
 * <p>The world- and unit-renderers are bound to the current round's terrain,
 * so they are rebuilt whenever the session swaps in a new round.
 *
 * <p>Determinism: the wall clock only scales how fast ticks are produced
 * ({@code session.advance(dt)}); the simulation itself is a pure function of
 * its seed.
 *
 * <p>Requires a display and an OpenGL 3.3 context; headless environments must
 * use {@link HeadlessGame}. Not thread-safe (single render thread).
 */
public final class WindowedGame implements AutoCloseable {

    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;
    private static final double ROTATE_RAD_PER_PIXEL = 0.01;
    private static final double SPEED_MIN = 0.25;
    private static final double SPEED_MAX = 32.0;

    private final AppWiring wiring;
    private final DefaultSession session;
    private final Window window;
    private final OrbitCamera camera;
    private final InputRouter inputRouter;
    private final HudRenderer hud;
    private final EffectsRenderer effects;
    private final SkyRenderer sky;
    private final HudOverlay overlay;
    private final Deque<UiCommand> pendingCommands = new ArrayDeque<>();

    private WorldRenderer world;
    private UnitRenderer units;
    private int currentRound = -1;
    private int completedRounds;
    private double lastSimSeconds;
    private Optional<UnitView> selectedUnit = Optional.empty();
    private double cursorX;
    private double cursorY;
    private boolean closed;

    /**
     * @param seed   the deterministic master seed (any 64-bit value)
     * @param config the round configuration (non-null)
     */
    public WindowedGame(long seed, SimConfig config) {
        this.wiring = new AppWiring(Objects.requireNonNull(config, "config"), seed);
        this.session = wiring.newSession(seed);
        this.window = new Window("Battle Sim", WINDOW_WIDTH, WINDOW_HEIGHT);
        double half = config.mapSize() / 2.0;
        this.camera = new OrbitCamera(
                (float) half, 0.0f, (float) half,
                (float) (config.mapSize() * 0.75), 0.785f, (float) (StrictMath.PI / 3.2),
                (float) (StrictMath.PI / 3.0), 16.0f / 9.0f, 0.1f, 20000.0f,
                20.0f, (float) (config.mapSize() * 3.0), 0.05f, (float) (StrictMath.PI - 0.05f));
        this.inputRouter = new InputRouter();
        this.hud = new HudRenderer();
        this.effects = new EffectsRenderer(seed);
        this.sky = new SkyRenderer();
        this.overlay = new HudOverlay();
        wireInput();
    }

    private void wireInput() {
        window.setKeyHandler((key, scancode, action, mods) ->
                inputRouter.onKey(key, action).ifPresent(pendingCommands::add));
        window.setMouseButtonHandler((button, action, mods) ->
                inputRouter.onMouseButton(button, action, cursorX, cursorY).ifPresent(pendingCommands::add));
        window.setMouseMoveHandler((x, y) -> {
            cursorX = x;
            cursorY = y;
            inputRouter.onMouseMove(x, y).ifPresent(pendingCommands::add);
        });
        window.setScrollHandler((xOffset, yOffset) ->
                inputRouter.onScroll(xOffset, yOffset).ifPresent(pendingCommands::add));
    }

    /** Runs the main loop until the window is closed (ESC or the title bar). */
    public void run() {
        if (closed) {
            throw new IllegalStateException("WindowedGame is closed");
        }
        glEnable(GL_DEPTH_TEST);
        double lastFrame = glfwGetTime();
        while (!window.shouldClose()) {
            double now = glfwGetTime();
            double dt = StrictMath.max(0.0, now - lastFrame);
            lastFrame = now;

            window.pollEvents();
            int width = Math.max(1, window.clientWidth());
            int height = Math.max(1, window.clientHeight());
            camera.setAspect((float) width / (float) height);
            glViewport(0, 0, window.framebufferWidth(), window.framebufferHeight());
            glClearColor(0.55f, 0.70f, 0.85f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            // 1) advance the simulation by the elapsed wall time
            session.advance(dt);
            SessionView view = session.view();
            onRoundChange(view);

            // 2) translate the queued UiCommands into session/camera actions
            List<UiCommand> commands = new ArrayList<>(pendingCommands);
            pendingCommands.clear();
            for (UiCommand command : commands) {
                handleCommand(command, view, width, height);
            }

            // 3) drive the presentation systems from the drained view
            RenderSnapshot snapshot = view.snapshot();
            WeatherState weather = snapshot.weather();
            double simSeconds = snapshot.timeSeconds();
            wiring.weatherDisplay().step(snapshot.tick());   // no-op for a re-stepped tick
            double effectDelta = StrictMath.max(0.0, simSeconds - lastSimSeconds);
            lastSimSeconds = simSeconds;

            effects.onEvents(view.events(), snapshot);
            effects.update(effectDelta);

            // 4) render: sky, world, units, effects, HUD
            double[] sun = view.terrain().sunDirection();
            sky.render(weather, simSeconds,
                    new Vector3f((float) sun[0], (float) sun[1], (float) sun[2]), camera);
            world.render(camera, lighting(weather, simSeconds, sun));
            units.render(snapshot, view.alpha(), camera, selectedUnit.map(UnitView::id));
            effects.render(camera);

            List<String> weatherLines = List.copyOf(wiring.weatherDisplay().conditions());
            HudModel hudModel = hud.render(view, selectedUnit, camera, width, height);
            overlay.render(width, height, hudModel, weatherLines);

            window.swapBuffers();
        }
    }

    private void onRoundChange(SessionView view) {
        if (view.roundNumber() != currentRound) {
            if (world != null) {
                world.close();
            }
            if (units != null) {
                units.close();
            }
            world = new WorldRenderer(view.terrain());
            units = new UnitRenderer(view.terrain());
            currentRound = view.roundNumber();
            lastSimSeconds = 0.0;
        }
        SessionStats stats = session.stats();
        if (stats.roundsCompleted() > completedRounds && stats.lastOutcome() != null) {
            for (int i = completedRounds; i < stats.roundsCompleted(); i++) {
                hud.recordOutcome(stats.lastOutcome());
            }
            completedRounds = stats.roundsCompleted();
        }
    }

    private void handleCommand(UiCommand command, SessionView view, int width, int height) {
        switch (command) {
            case UiCommand.TogglePause() -> session.setPaused(!view.paused());
            case UiCommand.SpeedUp() -> session.setSpeed(speedUp(view.speed()));
            case UiCommand.SpeedDown() -> session.setSpeed(speedDown(view.speed()));
            case UiCommand.FocusSelected() -> selectedUnit.ifPresent(unit ->
                    camera.follow((float) unit.x(), 0.0f, (float) unit.z()));
            case UiCommand.ResetCamera() -> camera.reset();
            case UiCommand.ToggleFullscreen() -> window.toggleFullscreen();
            case UiCommand.Click click ->
                    selectedUnit = Picker.pick(click.x(), click.y(), view, camera, width, height);
            case UiCommand.DragPan pan -> {
                float k = camera.radius() / 400.0f;   // pan speed scales with zoom level
                camera.pan((float) (pan.dx() * k), (float) (-pan.dy() * k));
            }
            case UiCommand.DragRotate rotate -> camera.rotate(
                    (float) (rotate.dx() * ROTATE_RAD_PER_PIXEL),
                    (float) (rotate.dy() * ROTATE_RAD_PER_PIXEL));
            case UiCommand.Zoom zoom -> camera.zoom((float) zoom.delta());
        }
    }

    private static double speedUp(double speed) {
        double base = speed < 1.0 ? 1.0 : speed;
        return StrictMath.min(SPEED_MAX, base * 2.0);
    }

    private static double speedDown(double speed) {
        double base = speed < 1.0 ? 1.0 : speed;
        return StrictMath.max(SPEED_MIN, base / 2.0);
    }

    private static LightingParams lighting(WeatherState w, double simSeconds, double[] sun) {
        return new LightingParams(
                new Vec3((float) sun[0], (float) sun[1], (float) sun[2]),
                new Vec3(1.0f, 0.95f, 0.85f),
                new Vec3(0.35f, 0.40f, 0.45f),
                new Vec3(0.62f, 0.72f, 0.82f),
                0.0004f,
                (float) simSeconds,
                (float) w.snowCover(),
                (float) w.wetness(),
                (float) w.puddles(),
                (float) w.windSpeed(),
                (float) w.windDirection());
    }

    /** Releases the session, the renderers, the window and GLFW. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        overlay.close();
        sky.close();
        effects.close();
        if (world != null) {
            world.close();
            world = null;
        }
        if (units != null) {
            units.close();
            units = null;
        }
        session.close();
        window.close();
        Window.shutdown();
    }
}