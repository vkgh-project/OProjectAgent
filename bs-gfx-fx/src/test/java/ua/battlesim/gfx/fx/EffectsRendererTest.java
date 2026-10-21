package ua.battlesim.gfx.fx;

import ua.battlesim.api.AttackEvent;
import ua.battlesim.api.RenderSnapshot;
import ua.battlesim.api.SimEvent;
import ua.battlesim.api.Side;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.UnitView;
import ua.battlesim.api.WeatherState;
import ua.battlesim.gfx.core.OrbitCamera;
import ua.battlesim.gfx.fx.internal.ParticlePool;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Contract tests for {@link EffectsRenderer} that do not require an OpenGL
 * context: argument validation (which happens before any GPU work) and safe,
 * idempotent disposal.
 */
class EffectsRendererTest {

    private static RenderSnapshot snapshot() {
        UnitView a = new UnitView(1, Side.BLUE, UnitType.TANK,
                0, 0, 0, 0, 0, 0, 0, 0, 100, UnitState.READY, 0, -1);
        UnitView t = new UnitView(2, Side.RED, UnitType.INFANTRY,
                240, 0, 240, 0, StrictMath.PI, StrictMath.PI, StrictMath.PI, StrictMath.PI, 100, UnitState.READY, 0, -1);
        WeatherState w = new WeatherState(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        return new RenderSnapshot(1, 0, 0.0, List.of(a, t), w);
    }

    @Test
    void defaults_poolCapacityAndTime() {
        try (EffectsRenderer r = new EffectsRenderer()) {
            assertEquals(ParticlePool.DEFAULT_CAPACITY, r.poolCapacity());
            assertEquals(24000, r.poolCapacity());
            assertEquals(0.0, r.simTime(), 1e-12);
            assertEquals(0, r.liveParticles());
        }
    }

    @Test
    void onEvents_populatesTheModel() {
        EffectsRenderer r = new EffectsRenderer(1L);
        List<SimEvent> events = List.of(new AttackEvent(0, 1, 2, 10, false));
        r.onEvents(events, snapshot());
        assertTrue(r.liveParticles() > 0, "muzzle flash + sparks + smoke");
        assertEquals(0.0, r.simTime(), 1e-12);
        r.update(0.5);
        assertEquals(0.5, r.simTime(), 1e-12);
        r.dispose();
    }

    @Test
    void update_validatesBeforeGpu() {
        EffectsRenderer r = new EffectsRenderer(1L);
        assertThrows(IllegalArgumentException.class, () -> r.update(-1.0));
        assertThrows(IllegalArgumentException.class, () -> r.update(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> r.update(Double.POSITIVE_INFINITY));
        r.dispose();
    }

    @Test
    void onEvents_validates() {
        EffectsRenderer r = new EffectsRenderer(1L);
        assertThrows(NullPointerException.class, () -> r.onEvents(null, snapshot()));
        assertThrows(NullPointerException.class, () -> r.onEvents(List.of(), null));
        assertThrows(NullPointerException.class, () -> r.onEvents(List.of((SimEvent) null), snapshot()));
        r.dispose();
    }

    @Test
    void render_validatesCameraBeforeGpu() {
        EffectsRenderer r = new EffectsRenderer(1L);
        assertThrows(NullPointerException.class, () -> r.render(null));
        OrbitCamera camera = new OrbitCamera();
        // The model is empty: rendering would still create the GPU; verify
        // instead that the spawn-centre plumbing is reachable without a crash.
        assertDoesNotThrow(() -> r.update(0.1));
        r.dispose();
        assertDoesNotThrow(camera::reset);
    }

    @Test
    void dispose_isIdempotent() {
        EffectsRenderer r = new EffectsRenderer(1L, 512);
        assertDoesNotThrow(r::dispose);
        assertDoesNotThrow(r::dispose);
        assertDoesNotThrow(r::close);
    }

    @Test
    void capacityTooSmall_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new EffectsRenderer(1L, 511));
    }

    /**
     * Regression: the partition scans must stay inside {@code [lo, hi]} —
     * unguarded scans ran off the array end (ArrayIndexOutOfBoundsException)
     * for some particle mixes, e.g. 11 live smoke/dust particles.
     */
    @Test
    void sortIndicesByDistanceDesc_ordersFarToNearWithDeterministicTies() throws Exception {
        java.lang.reflect.Method sort = EffectsRenderer.class.getDeclaredMethod(
                "sortIndicesByDistanceDesc", double[].class, int[].class, int.class);
        sort.setAccessible(true);

        java.util.Random rnd = new java.util.Random(7);
        for (int trial = 0; trial < 500; trial++) {
            int n = 1 + rnd.nextInt(64);
            double[] key = new double[n];
            int[] idx = new int[n];
            for (int k = 0; k < n; k++) {
                key[k] = rnd.nextInt(6) * 1.0; // coarse values: forces ties
                idx[k] = rnd.nextInt(1000);
            }
            int[] origIdx = idx.clone();
            double[] origKey = key.clone();
            Integer[] order = new Integer[n];
            for (int k = 0; k < n; k++) {
                order[k] = k;
            }
            java.util.Arrays.sort(order, (a, b) -> {
                int c = Double.compare(origKey[b], origKey[a]); // farthest first
                return c != 0 ? c : Integer.compare(origIdx[a], origIdx[b]);
            });
            int[] expect = new int[n];
            for (int k = 0; k < n; k++) {
                expect[k] = origIdx[order[k]];
            }
            sort.invoke(null, key, idx, n);
            for (int k = 0; k < n; k++) {
                assertEquals(expect[k], idx[k],
                        "trial " + trial + ", position " + k);
            }
        }
    }
}