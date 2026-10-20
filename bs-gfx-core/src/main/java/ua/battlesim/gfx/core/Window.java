package ua.battlesim.gfx.core;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.glViewport;

/**
 * GLFW window with OpenGL 3.3 core profile, 4x MSAA, and HiDPI support.
 *
 * <p>Starts fullscreen by default (using the primary monitor resolution).
 * Press F11 to toggle between fullscreen and windowed mode.
 *
 * <p>HiDPI: framebufferWidth() and framebufferHeight() return the actual
 * pixel size (accounting for content scale). clientWidth() and
 * clientHeight() return the logical size.
 *
 * <p><b>macOS:</b> the JVM must be started with -XstartOnFirstThread
 * for GLFW to work correctly.
 *
 * <p>Implements AutoCloseable for try-with-resources.
 */
public final class Window implements AutoCloseable {

    private static volatile boolean glfwInitialized = false;

    private final long handle;
    private final String title;
    private final long monitor;

    private int fbWidth = 1, fbHeight = 1;
    private int clientWidth = 1, clientHeight = 1;
    private float scaleX = 1.0f, scaleY = 1.0f;
    private boolean shouldClose = false;
    private boolean fullscreen = true;

    private int previousWindowX, previousWindowY;
    private int previousWindowW, previousWindowH;

    private KeyHandler keyHandler;
    private MouseButtonHandler mouseButtonHandler;
    private MouseMoveHandler mouseMoveHandler;
    private ScrollHandler scrollHandler;

    private GLFWKeyCallback keyCallback;
    private GLFWMouseButtonCallback mouseButtonCallback;
    private GLFWCursorPosCallback cursorPosCallback;
    private GLFWScrollCallback scrollCallback;
    private GLFWFramebufferSizeCallback framebufferSizeCallback;

    public Window(String title, int width, int height) {
        this(title, width, height, true);
    }

    public Window(String title, int width, int height, boolean fullscreen) {
        if (width <= 0 || height <= 0)
            throw new IllegalArgumentException("width and height must be > 0: " + width + ", " + height);

        this.title = title;
        ensureGlfwInitialized();

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_SAMPLES, 4);
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        this.monitor = glfwGetPrimaryMonitor();
        this.fullscreen = fullscreen;

        if (fullscreen) {
            GLFWVidMode vm = glfwGetVideoMode(monitor);
            if (vm != null) {
                handle = glfwCreateWindow(vm.width(), vm.height(), title, monitor, 0L);
            } else {
                handle = glfwCreateWindow(width, height, title, 0L, 0L);
                this.fullscreen = false;
            }
        } else {
            handle = glfwCreateWindow(width, height, title, 0L, 0L);
        }

        if (handle == 0L)
            throw new IllegalStateException("Failed to create GLFW window");

        glfwMakeContextCurrent(handle);

        // Fail fast, with an actionable message, when the environment cannot provide a
        // usable OpenGL context (e.g. a headless session, a remote/RDP session, or a
        // virtualized GPU). Some drivers still return a window handle in those cases,
        // so we verify the context is actually current BEFORE any GL call: otherwise the
        // first GL call below (glViewport in updateSize) is a native-level fatal abort
        // issued by lwjgl.dll that cannot be caught from Java.
        if (glfwGetCurrentContext() == 0L) {
            throw new IllegalStateException(
                    "An OpenGL window was created, but no context could be made current "
                    + "(glfwGetCurrentContext() == 0). This usually means the system has "
                    + "no usable hardware OpenGL -- e.g. a headless session, a remote/RDP "
                    + "session, or a virtualized GPU. Windowed mode requires a real GPU "
                    + "with an OpenGL 3.3 core context. On this machine, run headless "
                    + "instead (omit --windowed).");
        }

        // LWJGL tracks the current GL context in its own per-thread GLContext, which is
        // only bound once GL.createCapabilities() is called. glfwMakeContextCurrent()
        // alone (which is why glfwGetCurrentContext() above is non-zero) is NOT enough:
        // without this call the very first GL call (glViewport in updateSize below) trips
        // LWJGL's native fatal check "No context is current" and aborts the JVM. Call it
        // exactly once per context, right after making the context current.
        GL.createCapabilities();

        glfwSwapInterval(1);

        setupCallbacks();
        updateSize();
    }

    public long handle() { return handle; }
    public int framebufferWidth() { return fbWidth; }
    public int framebufferHeight() { return fbHeight; }
    public int clientWidth() { return clientWidth; }
    public int clientHeight() { return clientHeight; }
    public float scaleX() { return scaleX; }
    public float scaleY() { return scaleY; }
    public boolean isFullscreen() { return fullscreen; }
    public boolean shouldClose() { return shouldClose; }

    public void setShouldClose(boolean close) { this.shouldClose = close; }

    public void toggleFullscreen() {
        if (fullscreen) {
            glfwSetWindowMonitor(handle, 0L, previousWindowX, previousWindowY,
                    previousWindowW, previousWindowH, 60);
            fullscreen = false;
        } else {
            GLFWVidMode vm = glfwGetVideoMode(monitor);
            int[] w = {0}, h = {0};
            glfwGetWindowSize(handle, w, h);
            previousWindowW = w[0];
            previousWindowH = h[0];
            int[] px = {0}, py = {0};
            glfwGetWindowPos(handle, px, py);
            previousWindowX = px[0];
            previousWindowY = py[1];
            glfwSetWindowMonitor(handle, monitor, 0, 0,
                    vm.width(), vm.height(), vm.refreshRate());
            fullscreen = true;
        }
        updateSize();
    }

    public void pollEvents() {
        glfwPollEvents();
        updateSize();
    }

    public void swapBuffers() {
        glfwSwapBuffers(handle);
    }

    public void setKeyHandler(KeyHandler handler) { this.keyHandler = handler; }
    public void setMouseButtonHandler(MouseButtonHandler handler) { this.mouseButtonHandler = handler; }
    public void setMouseMoveHandler(MouseMoveHandler handler) { this.mouseMoveHandler = handler; }
    public void setScrollHandler(ScrollHandler handler) { this.scrollHandler = handler; }

    @Override
    public void close() {
        if (keyCallback != null) keyCallback.free();
        if (mouseButtonCallback != null) mouseButtonCallback.free();
        if (cursorPosCallback != null) cursorPosCallback.free();
        if (scrollCallback != null) scrollCallback.free();
        if (framebufferSizeCallback != null) framebufferSizeCallback.free();
        glfwDestroyWindow(handle);
    }

    public static void shutdown() {
        glfwTerminate();
        glfwInitialized = false;
    }

    @FunctionalInterface
    public interface KeyHandler {
        void onKey(int key, int scancode, int action, int mods);
    }

    @FunctionalInterface
    public interface MouseButtonHandler {
        void onMouseButton(int button, int action, int mods);
    }

    @FunctionalInterface
    public interface MouseMoveHandler {
        void onMouseMove(double x, double y);
    }

    @FunctionalInterface
    public interface ScrollHandler {
        void onScroll(double xOffset, double yOffset);
    }

    private static void ensureGlfwInitialized() {
        if (!glfwInitialized) {
            synchronized (Window.class) {
                if (!glfwInitialized) {
                    if (!glfwInit())
                        throw new IllegalStateException("Failed to initialize GLFW");
                    glfwInitialized = true;
                }
            }
        }
    }

    private void setupCallbacks() {
        keyCallback = GLFWKeyCallback.create(this::dispatchKey);
        glfwSetKeyCallback(handle, keyCallback);
        mouseButtonCallback = GLFWMouseButtonCallback.create(this::dispatchMouseButton);
        glfwSetMouseButtonCallback(handle, mouseButtonCallback);
        cursorPosCallback = GLFWCursorPosCallback.create(this::dispatchCursorPos);
        glfwSetCursorPosCallback(handle, cursorPosCallback);
        scrollCallback = GLFWScrollCallback.create(this::dispatchScroll);
        glfwSetScrollCallback(handle, scrollCallback);
        framebufferSizeCallback = GLFWFramebufferSizeCallback.create(this::onFramebufferSize);
        glfwSetFramebufferSizeCallback(handle, framebufferSizeCallback);
    }

    private void dispatchKey(long window, int key, int scancode, int action, int mods) {
        if (key == GLFW_KEY_F11 && action == GLFW_PRESS) {
            toggleFullscreen();
        }
        if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
            shouldClose = true;
        }
        if (keyHandler != null) keyHandler.onKey(key, scancode, action, mods);
    }

    private void dispatchMouseButton(long window, int button, int action, int mods) {
        if (mouseButtonHandler != null) mouseButtonHandler.onMouseButton(button, action, mods);
    }

    private void dispatchCursorPos(long window, double x, double y) {
        if (mouseMoveHandler != null) mouseMoveHandler.onMouseMove(x, y);
    }

    private void dispatchScroll(long window, double xOffset, double yOffset) {
        if (scrollHandler != null) scrollHandler.onScroll(xOffset, yOffset);
    }

    private void onFramebufferSize(long window, int width, int height) {
        fbWidth = width;
        fbHeight = height;
    }

    private void updateSize() {
        int[] w = {0}, h = {0};
        glfwGetWindowSize(handle, w, h);
        clientWidth = w[0];
        clientHeight = h[0];
        float[] sx = {0f}, sy = {0f};
        glfwGetWindowContentScale(handle, sx, sy);
        scaleX = sx[0];
        scaleY = sy[0];
        int[] fw = {0}, fh = {0};
        glfwGetFramebufferSize(handle, fw, fh);
        fbWidth = fw[0];
        fbHeight = fh[0];
        glViewport(0, 0, fbWidth, fbHeight);
    }
}
