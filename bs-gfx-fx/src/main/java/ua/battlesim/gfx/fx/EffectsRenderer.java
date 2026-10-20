package ua.battlesim.gfx.fx;

import ua.battlesim.api.RenderSnapshot;
import ua.battlesim.api.SimEvent;
import ua.battlesim.gfx.core.OrbitCamera;
import ua.battlesim.gfx.core.ShaderProgram;
import ua.battlesim.gfx.fx.internal.EffectsModel;
import ua.battlesim.gfx.fx.internal.FxShaders;
import ua.battlesim.gfx.fx.internal.ParticlePool;
import ua.battlesim.gfx.fx.internal.ParticleState;
import ua.battlesim.gfx.fx.internal.ParticleType;
import ua.battlesim.gfx.fx.internal.ParticleVao;
import ua.battlesim.gfx.fx.internal.SolidVao;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.Objects;

/**
 * Renders the battle effects of a round: muzzle flashes, tracer shells,
 * impact explosions (flash, fire, dust, sparks, smoke), continuously smoking
 * and burning wrecks, plus rain streaks and snow flakes around the camera
 * target.
 *
 * <p>Public API of {@code bs-gfx-fx} (Layer 3); depends only on
 * {@code bs-api} and {@code bs-gfx-core}.
 *
 * <p>All effects are driven by <em>simulated</em> time only:
 * {@link #onEvents(List, RenderSnapshot)} registers the visual consequences
 * of events at the current simulated second and {@link #update(double)}
 * advances the effect simulation — so pause and playback speed are respected
 * automatically. Every random choice comes from a fixed deterministic
 * sequence (seed), all trigonometry uses {@link StrictMath}, and no hash
 * iteration order ever influences the result.
 *
 * <p>The particle pool has about 24000 slots (see
 * {@link ParticlePool#DEFAULT_CAPACITY}), partitioned per effect type; smoke
 * is drawn distance-sorted (far to near) for correct soft overlap and fire,
 * sparks and flashes use additive blending.
 *
 * <p>Threading: not thread-safe; drive it from the single render thread and
 * call {@link #dispose} before releasing the context. The event and update
 * steps are pure CPU and need no OpenGL; the GPU resources (shaders +
 * buffers) are created lazily on the first {@link #render} call, which
 * requires a current context.
 *
 * <p>Implements AutoCloseable for try-with-resources (delegates to
 * {@link #dispose}).
 */
public final class EffectsRenderer implements AutoCloseable {

    private final EffectsModel model;
    private double spawnCx;
    private double spawnCy;
    private double spawnCz;

    private ShaderProgram particleShader;
    private ShaderProgram solidShader;
    private ParticleVao particleVao;
    private SolidVao solidVao;
    private boolean gpuReady;

    /**
     * Creates a renderer with the default deterministic seed and the default
     * pool capacity (about 24000 particles).
     */
    public EffectsRenderer() {
        this(EffectsModel.DEFAULT_SEED, ParticlePool.DEFAULT_CAPACITY);
    }

    /**
     * @param seed the deterministic seed for all random effect choices
     */
    public EffectsRenderer(long seed) {
        this(seed, ParticlePool.DEFAULT_CAPACITY);
    }

    /**
     * @param seed      the deterministic seed for all random effect choices
     * @param capacity  total particle pool size, {@code >= 512}
     */
    public EffectsRenderer(long seed, int capacity) {
        this.model = new EffectsModel(seed, capacity);
    }

    /** @return the number of live particles across all categories */
    public int liveParticles() {
        int n = 0;
        for (ParticleType type : ParticleType.values()) {
            n += model.liveCount(type);
        }
        return n;
    }

    /** @return the total particle pool capacity in slots */
    public int poolCapacity() {
        return model.pool().capacity();
    }

    /** @return the current simulated time in seconds */
    public double simTime() {
        return model.simTime();
    }

    /**
     * Registers the visual consequences of a batch of drained simulation
     * events at the current simulated second (muzzle flash and tracer for
     * attacks, the wreck registration for destructions).
     *
     * @param events    the drained events (non-null, elements non-null)
     * @param snapshot  the round state delivering the events (non-null)
     */
    public void onEvents(List<SimEvent> events, RenderSnapshot snapshot) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(snapshot, "snapshot");
        model.onEvents(events, snapshot);
    }

    /**
     * Advances the effect simulation by {@code simSeconds} simulated seconds
     * (shells fly, explosions bloom, wrecks emit smoke and fire, precipitation
     * falls). This is the only way time moves here, so pause and playback
     * speed are respected automatically.
     *
     * @param simSeconds simulated seconds, finite and {@code >= 0}
     */
    public void update(double simSeconds) {
        model.update(simSeconds, spawnCx, spawnCy, spawnCz);
    }

    /**
     * Draws all current effects for one frame.
     *
     * <p>Pass order: (1) alpha-blended soft smoke, dust and precipitation,
     * each sorted far-to-near by distance to the eye; (2) additive flashes,
     * fire, sparks, tracers and shell tips. The camera target of this call
     * becomes the rain/snow spawn centre for the following updates.
     *
     * <p>Requires a current OpenGL context; the GPU resources are created on
     * the first call.
     *
     * @param camera the view/projection camera (non-null)
     */
    public void render(OrbitCamera camera) {
        Objects.requireNonNull(camera, "camera");
        if (!gpuReady) {
            createGpu();
        }
        spawnCx = camera.targetX();
        spawnCy = camera.targetY();
        spawnCz = camera.targetZ();

        double time = model.simTime();
        Vector3f eye = camera.eyePosition();
        Matrix4f view = camera.viewMatrix();
        float rx = view.m00(), ry = view.m10(), rz = view.m20();
        float ux = view.m01(), uy = view.m11(), uz = view.m21();

        int smoke = model.pool().liveCount(ParticleType.SMOKE);
        int dust = model.pool().liveCount(ParticleType.DUST);
        int rain = model.pool().liveCount(ParticleType.RAIN);
        int snow = model.pool().liveCount(ParticleType.SNOW);
        int muzzle = model.pool().liveCount(ParticleType.MUZZLE_FLASH);
        int impact = model.pool().liveCount(ParticleType.IMPACT_FLASH);
        int fire = model.pool().liveCount(ParticleType.FIRE);
        int spark = model.pool().liveCount(ParticleType.SPARK);
        int tracers = model.tracers().count();
        int alphaParticles = smoke + dust + rain + snow;
        int additiveParticles = muzzle + impact + fire + spark + tracers;
        float[] alpha = new float[alphaParticles * 9];
        float[] additive = new float[additiveParticles * 9];
        float[] solid = new float[tracers * 6 * 7];

        int o = 0;
        o += writeParticles(alpha, o, ParticleType.SMOKE, eye.x(), eye.y(), eye.z(), true);
        o += writeParticles(alpha, o, ParticleType.DUST, eye.x(), eye.y(), eye.z(), true);
        o += writeParticles(alpha, o, ParticleType.RAIN, eye.x(), eye.y(), eye.z(), true);
        o += writeParticles(alpha, o, ParticleType.SNOW, eye.x(), eye.y(), eye.z(), true);

        o = 0;
        o += writeParticles(additive, o, ParticleType.MUZZLE_FLASH, 0, 0, 0, false);
        o += writeParticles(additive, o, ParticleType.IMPACT_FLASH, 0, 0, 0, false);
        o += writeParticles(additive, o, ParticleType.FIRE, 0, 0, 0, false);
        o += writeParticles(additive, o, ParticleType.SPARK, 0, 0, 0, false);
        for (int i = 0; i < tracers; i++) {
            double p = model.tracers().progress(i, time);
            if (p <= 0.02) {
                continue;
            }
            double tx = model.tracers().tipX(i, time);
            double ty = model.tracers().tipY(i, time);
            double tz = model.tracers().tipZ(i, time);
            int t = o + i * 9;
            additive[t] = (float) tx;
            additive[t + 1] = (float) ty;
            additive[t + 2] = (float) tz;
            additive[t + 3] = 0.5f;
            additive[t + 4] = 0.5f;
            additive[t + 5] = 1.0f;
            additive[t + 6] = 0.9f;
            additive[t + 7] = 0.6f;
            additive[t + 8] = 1.0f;
            double back = Math.max(0.0, p - 0.12);
            double sx = lerp(model.tracers().endX(i), tx, back);
            double sy = lerp(model.tracers().endY(i), ty, back);
            double sz = lerp(model.tracers().endZ(i), tz, back);
            if (distance2(sx, sy, sz, tx, ty, tz) > 1.0e-6) {
                writeTracerQuad(solid, i * 6 * 7, sx, sy, sz, tx, ty, tz, ux, uy, uz, rx, ry, rz);
            }
        }

        // --- Draw: alpha pass, then additive pass ---
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL11.glDepthMask(false);

        particleShader.use();
        particleShader.setMat4("uViewProj", camera.viewProjectionMatrix());
        particleShader.setVec3("uRight", new Vector3f(rx, ry, rz));
        particleShader.setVec3("uUp", new Vector3f(ux, uy, uz));

        org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        particleVao.upload(alpha, alphaParticles);
        particleVao.draw();

        org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_ONE,
                org.lwjgl.opengl.GL11.GL_ONE);
        particleVao.upload(additive, additiveParticles);
        particleVao.draw();

        solidShader.use();
        solidShader.setMat4("uViewProj", camera.viewProjectionMatrix());
        solidVao.upload(solid, tracers * 6);
        solidVao.draw();

        ShaderProgram.unuse();
        org.lwjgl.opengl.GL11.glDepthMask(true);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_CULL_FACE);
    }

    /**
     * Releases all GPU resources. Safe to call even when {@link #render} was
     * never called (and thus the resources were never created), and safe to
     * call more than once.
     */
    public void dispose() {
        if (particleVao != null) {
            particleVao.close();
            particleVao = null;
        }
        if (solidVao != null) {
            solidVao.close();
            solidVao = null;
        }
        if (particleShader != null) {
            particleShader.close();
            particleShader = null;
        }
        if (solidShader != null) {
            solidShader.close();
            solidShader = null;
        }
        gpuReady = false;
    }

    @Override
    public void close() {
        dispose();
    }

    // --- Internal helpers (CPU side, no OpenGL) ---

    private void createGpu() {
        this.particleShader = new ShaderProgram(FxShaders.particleVertex(), FxShaders.particleFragment());
        this.solidShader = new ShaderProgram(FxShaders.solidVertex(), FxShaders.solidFragment());
        this.particleVao = new ParticleVao();
        this.solidVao = new SolidVao();
        this.gpuReady = true;
    }

    /**
     * Writes the live particles of one category into {@code out} starting at
     * float offset {@code offset}, optionally sorted far-to-near by distance
     * to the eye (deterministic: distance descending, slot index ascending as
     * tie-break).
     *
     * @return the number of floats written
     */
    private int writeParticles(float[] out, int offset, ParticleType type,
            double eyeX, double eyeY, double eyeZ, boolean sort) {
        ParticlePool pool = model.pool();
        int block = pool.blockSize(type);
        int live = pool.liveCount(type);
        if (live == 0) {
            return 0;
        }
        int[] idx = new int[live];
        double[] dist = new double[live];
        int n = 0;
        for (int l = 0; l < block; l++) {
            if (pool.isAlive(type, l)) {
                idx[n] = l;
                ParticleState s = pool.state(type, l);
                double dx = s.x() - eyeX;
                double dy = s.y() - eyeY;
                double dz = s.z() - eyeZ;
                dist[n] = dx * dx + dy * dy + dz * dz;
                n++;
            }
        }
        if (sort) {
            sortIndicesByDistanceDesc(dist, idx, n);
        }
        int written = 0;
        for (int k = 0; k < n; k++) {
            ParticleState s = pool.state(type, idx[k]);
            int t = offset + written;
            out[t] = (float) s.x();
            out[t + 1] = (float) s.y();
            out[t + 2] = (float) s.z();
            out[t + 3] = (float) s.size();
            out[t + 4] = (float) (type == ParticleType.RAIN ? s.size() * 10.0 : s.size());
            out[t + 5] = (float) s.r();
            out[t + 6] = (float) s.g();
            out[t + 7] = (float) s.b();
            out[t + 8] = (float) s.alpha();
            written += 9;
        }
        return written;
    }

    /** Deterministic quicksort: distance descending, slot index ascending as tie-break. */
    private static void sortIndicesByDistanceDesc(double[] key, int[] idx, int n) {
        quicksort(key, idx, 0, n - 1);
    }

    private static void quicksort(double[] key, int[] idx, int lo, int hi) {
        while (lo < hi) {
            int i = lo;
            int j = hi;
            int pivotIdx = (lo + hi) >>> 1;
            double pivotKey = key[pivotIdx];
            while (i <= j) {
                while (compare(key, idx, i, pivotKey, pivotIdx) < 0) {
                    i++;
                }
                while (compare(key, idx, j, pivotKey, pivotIdx) > 0) {
                    j--;
                }
                if (i <= j) {
                    int tmp = idx[i];
                    idx[i] = idx[j];
                    idx[j] = tmp;
                    i++;
                    j--;
                }
            }
            if (j - lo < hi - i) {
                if (lo < j) {
                    quicksort(key, idx, lo, j);
                }
                lo = i;
            } else {
                if (i < hi) {
                    quicksort(key, idx, i, hi);
                }
                hi = j;
            }
        }
    }

    private static int compare(double[] key, int[] idx, int i, double pivotKey, int pivotIdx) {
        int c = Double.compare(key[i], pivotKey);
        if (c != 0) {
            return -c; // farthest first
        }
        return Integer.compare(idx[i], pivotIdx);
    }

    /**
     * Writes one camera-facing tracer quad (2 triangles, 6 vertices) at float
     * offset {@code offset} (needs 42 floats).
     */
    private static void writeTracerQuad(float[] out, int offset,
            double sx, double sy, double sz, double tx, double ty, double tz,
            float ux, float uy, float uz, float rx, float ry, float rz) {
        double dx = tx - sx;
        double dy = ty - sy;
        double dz = tz - sz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-9) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        // side = normalize(cross(dir, viewUp))
        double cx = dy * uz - dz * uy;
        double cy = dz * ux - dx * uz;
        double cz = dx * uy - dy * ux;
        double cl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (cl < 1.0e-6) {
            // degenerate: the tracer runs along the view axis
            cx = rx;
            cy = ry;
            cz = rz;
        } else {
            cx /= cl;
            cy /= cl;
            cz /= cl;
        }
        double w = 0.35;
        writeVertex(out, offset + 0 * 7, sx - cx * w, sy - cy * w, sz - cz * w);
        writeVertex(out, offset + 1 * 7, sx + cx * w, sy + cy * w, sz + cz * w);
        writeVertex(out, offset + 2 * 7, tx + cx * w, ty + cy * w, tz + cz * w);
        writeVertex(out, offset + 3 * 7, sx - cx * w, sy - cy * w, sz - cz * w);
        writeVertex(out, offset + 4 * 7, tx + cx * w, ty + cy * w, tz + cz * w);
        writeVertex(out, offset + 5 * 7, tx - cx * w, ty - cy * w, tz - cz * w);
    }

    private static void writeVertex(float[] out, int offset, double x, double y, double z) {
        out[offset] = (float) x;
        out[offset + 1] = (float) y;
        out[offset + 2] = (float) z;
        out[offset + 3] = 1.0f;
        out[offset + 4] = 0.85f;
        out[offset + 5] = 0.5f;
        out[offset + 6] = 1.0f;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double distance2(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        return dx * dx + dy * dy + dz * dz;
    }
}