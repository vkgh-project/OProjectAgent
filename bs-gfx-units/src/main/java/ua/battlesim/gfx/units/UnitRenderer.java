package ua.battlesim.gfx.units;

import org.joml.Vector3f;
import ua.battlesim.api.RenderSnapshot;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitView;
import ua.battlesim.api.WeatherState;
import ua.battlesim.gfx.core.OrbitCamera;
import ua.battlesim.gfx.core.ShaderProgram;
import ua.battlesim.gfx.units.internal.DynamicMesh;
import ua.battlesim.gfx.units.internal.FloatList;
import ua.battlesim.gfx.units.internal.Shaders;
import ua.battlesim.gfx.units.internal.UnitModels;
import ua.battlesim.gfx.units.internal.UnitPose;
import ua.battlesim.gfx.units.internal.UnitVertexWriter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Renders the battle units of a round from a {@link RenderSnapshot}.
 *
 * <p>Public API of {@code bs-gfx-units} (Layer 3); depends only on {@code bs-api}
 * and {@code bs-gfx-core}. Procedural models:
 * <ul>
 *   <li><b>tank</b> — hull, two tracks with a scrolling tread, four road wheels,
 *       a turret and a gun that points along the heading while moving and
 *       swings toward the target while attacking (with recoil);</li>
 *   <li><b>APC infantry</b> — eight wheels, a turret and three seated soldiers;</li>
 *   <li><b>infantry</b> — a squad of four soldiers with walking legs.</li>
 * </ul>
 *
 * <p>The side colour is applied to the paint (hull / torso / head) and the
 * vest. Position and heading are interpolated between the previous and current
 * snapshot values with {@code alpha}; the model tilts to the terrain, wheels
 * spin and legs swing from the odometer, destroyed units are charred and tipped
 * over, soft blob shadows are drawn under each unit, and a bright ring marks the
 * selected unit. A strong ambient fill keeps the models readable in any light.
 *
 * <p>Threading: not thread-safe; drive it from the single render thread and
 * call {@link #dispose} before releasing the context. The construction step is
 * pure CPU and needs no OpenGL; the GPU resources (shader + buffers) are created
 * lazily on the first {@link #render} call, which requires a current context.
 *
 * <p>Implements AutoCloseable for try-with-resources (delegates to
 * {@link #dispose}).
 */
public final class UnitRenderer implements AutoCloseable {

    private final Terrain terrain;
    private ShaderProgram shader;
    private DynamicMesh opaque;
    private DynamicMesh shadow;
    private boolean gpuReady;

    /**
     * @param terrain the round terrain (non-null), queried for height and sun
     */
    public UnitRenderer(Terrain terrain) {
        Objects.requireNonNull(terrain, "terrain");
        this.terrain = terrain;
    }

    /**
     * Renders every unit in the snapshot for one frame.
     *
     * @param snapshot   the immutable round state (non-null)
     * @param alpha      interpolation in {@code [0, 1]} between the previous and current frame
     * @param camera     the view/projection camera (non-null)
     * @param selectedId the id of the currently selected unit, or empty (may be null)
     */
    public void render(RenderSnapshot snapshot, double alpha, OrbitCamera camera, Optional<Integer> selectedId) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(camera, "camera");
        if (Double.isNaN(alpha) || alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("alpha must be in [0, 1]: " + alpha);
        }
        Optional<Integer> sel = (selectedId == null) ? Optional.empty() : selectedId;
        if (!gpuReady) {
            createGpu();
        }

        FloatList opaqueList = new FloatList(8192);
        FloatList shadowList = new FloatList(2048);
        List<UnitView> units = snapshot.units();
        for (int i = 0; i < units.size(); i++) {
            UnitView u = units.get(i);
            UnitPose pose = UnitPose.from(u, snapshot.tick(), alpha, terrain, sel);
            UnitVertexWriter.write(UnitModels.forType(u.type()), pose, opaqueList, shadowList);
        }

        boolean opaqueEmpty = opaqueList.isEmpty();
        boolean shadowEmpty = shadowList.isEmpty();
        if (opaqueEmpty) {
            opaque.clear();
        } else {
            opaque.upload(opaqueList.toFloat());
        }
        if (shadowEmpty) {
            shadow.clear();
        } else {
            shadow.upload(shadowList.toFloat());
        }
        draw(camera, snapshot, opaqueEmpty, shadowEmpty);
    }

    private void draw(OrbitCamera camera, RenderSnapshot snapshot, boolean opaqueEmpty, boolean shadowEmpty) {
        shader.use();

        double[] sun = terrain.sunDirection();
        double sl = StrictMath.sqrt(sun[0] * sun[0] + sun[1] * sun[1] + sun[2] * sun[2]);
        if (sl < 1.0e-9) {
            sl = 1.0;
        }
        Vector3f sunDir = new Vector3f((float) (sun[0] / sl), (float) (sun[1] / sl), (float) (sun[2] / sl));

        WeatherState w = snapshot.weather();
        double cloud = clamp01(w.cloud());
        double rain = clamp01(w.rain()) + clamp01(w.heavyRain());
        double snow = clamp01(w.snow());
        float fogR = (float) lerp(lerp(0.62, 0.50, cloud), 0.92, 0.4 * snow);
        float fogG = (float) lerp(lerp(0.72, 0.52, cloud), 0.94, 0.4 * snow);
        float fogB = (float) lerp(lerp(0.82, 0.55, cloud), 0.96, 0.4 * snow);
        double fogDensity = 0.00035 * (1.0 + 2.5 * rain + 1.5 * snow);

        shader.setMat4("uViewProj", camera.viewProjectionMatrix());
        shader.setVec3("uSunDir", sunDir);
        shader.setVec3("uSunColor", new Vector3f(1.0f, 0.95f, 0.85f));
        shader.setVec3("uAmbient", new Vector3f(0.45f, 0.48f, 0.52f));
        shader.setVec3("uCamPos", camera.eyePosition());
        shader.setVec3("uFogColor", new Vector3f(fogR, fogG, fogB));
        shader.setFloat("uFogDensity", (float) fogDensity);

        if (!opaqueEmpty) {
            opaque.draw();
        }

        if (!shadowEmpty) {
            org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
            org.lwjgl.opengl.GL11.glDepthMask(false);
            org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                    org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
            shadow.draw();
            org.lwjgl.opengl.GL11.glDepthMask(true);
            org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
        }

        ShaderProgram.unuse();
    }

    private void createGpu() {
        this.shader = new ShaderProgram(Shaders.vertex(), Shaders.fragment());
        this.opaque = new DynamicMesh();
        this.shadow = new DynamicMesh();
        this.gpuReady = true;
    }

    /**
     * Releases all GPU resources. Safe to call even when {@link #render} was
     * never called (and thus the resources were never created), and safe to call
     * more than once.
     */
    public void dispose() {
        closeQuietly(opaque);
        opaque = null;
        closeQuietly(shadow);
        shadow = null;
        if (shader != null) {
            shader.close();
            shader = null;
        }
        gpuReady = false;
    }

    private static void closeQuietly(DynamicMesh m) {
        if (m != null) {
            m.close();
        }
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    @Override
    public void close() {
        dispose();
    }
}