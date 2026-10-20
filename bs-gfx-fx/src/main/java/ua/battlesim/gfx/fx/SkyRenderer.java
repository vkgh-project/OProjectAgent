package ua.battlesim.gfx.fx;

import ua.battlesim.api.WeatherState;
import ua.battlesim.gfx.core.OrbitCamera;
import ua.battlesim.gfx.core.ShaderProgram;
import ua.battlesim.gfx.fx.internal.FxShaders;
import ua.battlesim.gfx.fx.internal.Lightning;
import ua.battlesim.gfx.fx.internal.SkyVao;

import org.joml.Vector3f;

import java.util.Objects;

/**
 * Renders the sky: a gradient dome derived from the {@link WeatherState}, a
 * sun with halo, procedural clouds, and a lightning flash during heavy
 * rain.
 *
 * <p>Public API of {@code bs-gfx-fx} (Layer 3); depends only on
 * {@code bs-api} and {@code bs-gfx-core}.
 *
 * <p>The sky is driven by simulated time only (the lightning schedule is a
 * deterministic function of the simulated second and the heavy-rain
 * intensity — see {@link #lightningFlash(WeatherState, double)}), so pause
 * and playback speed are respected. The light colour to feed the world
 * lighting comes from the {@link SkyColors} palette.
 *
 * <p>Threading: not thread-safe; drive it from the single render thread and
 * call {@link #dispose} before releasing the context. The palette and
 * lightning math are pure CPU and need no OpenGL; the GPU resources are
 * created lazily on the first {@link #render} call, which requires a current
 * context. Draw the sky first in the frame (it disables the depth test for
 * its call and restores the state afterwards).
 *
 * <p>Implements AutoCloseable for try-with-resources (delegates to
 * {@link #dispose}).
 */
public final class SkyRenderer implements AutoCloseable {

    private ShaderProgram shader;
    private SkyVao vao;
    private boolean gpuReady;

    /** Creates a sky renderer (deterministic lightning schedule). */
    public SkyRenderer() {
    }

    /**
     * The sky and light palette for a weather state (pure, deterministic).
     *
     * @param weather the weather state (non-null)
     * @return the palette
     */
    public static SkyColors colors(WeatherState weather) {
        return SkyColors.from(weather);
    }

    /**
     * The deterministic lightning flash intensity at the given simulated
     * second (0 without heavy rain).
     *
     * @param weather     the weather state (non-null)
     * @param simSeconds  simulated seconds, finite and {@code >= 0}
     * @return the flash intensity in {@code [0, 1]}
     */
    public static double lightningFlash(WeatherState weather, double simSeconds) {
        return Lightning.flash(weather, simSeconds);
    }

    /**
     * Draws the sky dome for one frame.
     *
     * <p>Requires a current OpenGL context; the GPU resources are created on
     * the first call.
     *
     * @param weather       the weather state (non-null)
     * @param simSeconds    simulated seconds, finite and {@code >= 0}
     * @param sunDirection  the (non-normalized) sun direction in the world
     *                      (x east, y up, z south), non-null and finite
     * @param camera        the view/projection camera (non-null)
     */
    public void render(WeatherState weather, double simSeconds, Vector3f sunDirection, OrbitCamera camera) {
        Objects.requireNonNull(weather, "weather");
        if (!Double.isFinite(simSeconds) || simSeconds < 0.0) {
            throw new IllegalArgumentException("simSeconds must be finite and >= 0: " + simSeconds);
        }
        Objects.requireNonNull(sunDirection, "sunDirection");
        for (double v : new double[] { sunDirection.x(), sunDirection.y(), sunDirection.z() }) {
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("sunDirection must be finite, got: " + v);
            }
        }
        Objects.requireNonNull(camera, "camera");
        if (!gpuReady) {
            createGpu();
        }

        SkyColors palette = SkyColors.from(weather);
        double flash = Lightning.flash(weather, simSeconds);

        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
        org.lwjgl.opengl.GL11.glDepthMask(false);

        shader.use();
        shader.setMat4("uInvViewProj", camera.inverseViewProjectionMatrix());
        shader.setVec3("uEye", camera.eyePosition());
        shader.setVec3("uTop", new Vector3f((float) palette.topR(), (float) palette.topG(), (float) palette.topB()));
        shader.setVec3("uHorizon", new Vector3f((float) palette.horizonR(), (float) palette.horizonG(), (float) palette.horizonB()));
        shader.setVec3("uSunDir", sunDirection);
        shader.setVec3("uSunColor", new Vector3f((float) palette.sunR(), (float) palette.sunG(), (float) palette.sunB()));
        shader.setVec3("uCloudColor", new Vector3f((float) palette.cloudR(), (float) palette.cloudG(), (float) palette.cloudB()));
        shader.setFloat("uCloudAmount", (float) palette.cloudAmount());
        shader.setFloat("uFlash", (float) flash);
        vao.draw();

        ShaderProgram.unuse();
        org.lwjgl.opengl.GL11.glDepthMask(true);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_DEPTH_TEST);
    }

    /**
     * Releases all GPU resources. Safe to call even when {@link #render} was
     * never called (and thus the resources were never created), and safe to
     * call more than once.
     */
    public void dispose() {
        if (vao != null) {
            vao.close();
            vao = null;
        }
        if (shader != null) {
            shader.close();
            shader = null;
        }
        gpuReady = false;
    }

    @Override
    public void close() {
        dispose();
    }

    private void createGpu() {
        this.shader = new ShaderProgram(FxShaders.skyVertex(), FxShaders.skyFragment());
        this.vao = new SkyVao();
        this.gpuReady = true;
    }
}