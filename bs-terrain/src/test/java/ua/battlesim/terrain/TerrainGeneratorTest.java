package ua.battlesim.terrain;

import org.junit.jupiter.api.Test;
import ua.battlesim.api.HeightField;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.Vec2;
import ua.battlesim.api.WaterField;
import ua.battlesim.math.FloatGrid;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerrainGeneratorTest {

    private static final SimConfig CFG = SimConfig.defaults();

    @Test
    void determinism_sameSeedProducesIdenticalResult() {
        long seed = 42L;
        TerrainBase t1 = TerrainGenerator.generate(seed, CFG);
        TerrainBase t2 = TerrainGenerator.generate(seed, CFG);
        double[] samples = {100, 300, 500, 1000, 1500, 1900};
        for (double x : samples) {
            for (double z : samples) {
                assertEquals(t1.height().heightAt(x, z), t2.height().heightAt(x, z), 0.0);
                assertEquals(t1.water().depthAt(x, z), t2.water().depthAt(x, z), 0.0);
            }
        }
        assertArrayEquals(t1.sunDirection(), t2.sunDirection());
        int gw = t1.forestDensity().width();
        int gh = t1.forestDensity().height();
        for (int j = 0; j < gh; j += 64) {
            for (int i = 0; i < gw; i += 64) {
                assertEquals(t1.forestDensity().get(i, j), t2.forestDensity().get(i, j));
                assertEquals(t1.shade().get(i, j), t2.shade().get(i, j));
            }
        }
    }

    @Test
    void differentSeedsProduceDifferentResults() {
        TerrainBase t1 = TerrainGenerator.generate(1L, CFG);
        TerrainBase t2 = TerrainGenerator.generate(2L, CFG);
        boolean anyDifferent = false;
        for (double x : new double[]{100, 500, 1000, 1500}) {
            for (double z : new double[]{100, 500, 1000, 1500}) {
                if (t1.height().heightAt(x, z) != t2.height().heightAt(x, z)) {
                    anyDifferent = true; break;
                }
            }
            if (anyDifferent) break;
        }
        assertTrue(anyDifferent);
    }

    @Test
    void noNaNInHeights() {
        TerrainBase t = TerrainGenerator.generate(123L, CFG);
        int gw = t.forestDensity().width();
        int gh = t.forestDensity().height();
        for (int j = 0; j < gh; j += 8) {
            for (int i = 0; i < gw; i += 8) {
                assertTrue(Double.isFinite(t.height().heightAt(i * 4.0, j * 4.0)));
            }
        }
    }

    @Test
    void noNaNInWater() {
        TerrainBase t = TerrainGenerator.generate(456L, CFG);
        int gw = t.forestDensity().width();
        int gh = t.forestDensity().height();
        for (int j = 0; j < gh; j += 8) {
            for (int i = 0; i < gw; i += 8) {
                assertTrue(Double.isFinite(t.water().depthAt(i * 4.0, j * 4.0)));
                assertTrue(Double.isFinite(t.water().swampAt(i * 4.0, j * 4.0)));
                Vec2 flow = t.water().flowAt(i * 4.0, j * 4.0);
                assertTrue(Double.isFinite(flow.x()));
                assertTrue(Double.isFinite(flow.z()));
            }
        }
    }

    @Test
    void noNaNInShadeAndForest() {
        TerrainBase t = TerrainGenerator.generate(789L, CFG);
        int gw = t.forestDensity().width();
        int gh = t.forestDensity().height();
        for (int j = 0; j < gh; j += 16) {
            for (int i = 0; i < gw; i += 16) {
                assertTrue(Float.isFinite(t.shade().get(i, j)));
                assertTrue(Float.isFinite(t.forestDensity().get(i, j)));
            }
        }
        for (double d : t.sunDirection()) assertTrue(Double.isFinite(d));
    }

    @Test
    void depthNeverNegative() {
        TerrainBase t = TerrainGenerator.generate(1000L, CFG);
        int gw = t.forestDensity().width();
        int gh = t.forestDensity().height();
        for (int j = 0; j < gh; j += 4) {
            for (int i = 0; i < gw; i += 4) {
                double d = t.water().depthAt(i * 4.0, j * 4.0);
                assertTrue(d >= 0.0, "negative depth " + d);
            }
        }
    }

    @Test
    void swampInRange() {
        TerrainBase t = TerrainGenerator.generate(2000L, CFG);
        int gw = t.forestDensity().width();
        int gh = t.forestDensity().height();
        for (int j = 0; j < gh; j += 8) {
            for (int i = 0; i < gw; i += 8) {
                double s = t.water().swampAt(i * 4.0, j * 4.0);
                assertTrue(s >= 0.0 && s <= 1.0, "swamp out of range: " + s);
            }
        }
    }

    @Test
    void shadeInRange() {
        TerrainBase t = TerrainGenerator.generate(3000L, CFG);
        int gw = t.shade().width();
        int gh = t.shade().height();
        for (int j = 0; j < gh; j += 8) {
            for (int i = 0; i < gw; i += 8) {
                float s = t.shade().get(i, j);
                assertTrue(s >= 0.0f && s <= 1.0f, "shade out of range: " + s);
            }
        }
    }

    @Test
    void forestInRange() {
        TerrainBase t = TerrainGenerator.generate(4000L, CFG);
        int gw = t.forestDensity().width();
        int gh = t.forestDensity().height();
        for (int j = 0; j < gh; j += 8) {
            for (int i = 0; i < gw; i += 8) {
                float f = t.forestDensity().get(i, j);
                assertTrue(f >= 0.0f && f <= 1.0f, "forest out of range: " + f);
            }
        }
    }

    @Test
    void riverLevelNeverRisesDownstream() {
        List<double[]> levels = TerrainGenerator.riverLevels(42L, CFG);
        assertFalse(levels.isEmpty(), "at least one river should be generated");
        for (double[] lv : levels) {
            assertTrue(lv.length >= 2, "river should have at least 2 levels");
            for (int i = 1; i < lv.length; i++) {
                assertTrue(lv[i] <= lv[i - 1] + 1e-9,
                        "river level rises at " + i + ": " + lv[i - 1] + " -> " + lv[i]);
            }
        }
    }

    @Test
    void sunDirectionIsUnitVector() {
        TerrainBase t = TerrainGenerator.generate(5000L, CFG);
        double[] sd = t.sunDirection();
        assertEquals(3, sd.length);
        double len = StrictMath.sqrt(sd[0] * sd[0] + sd[1] * sd[1] + sd[2] * sd[2]);
        assertEquals(1.0, len, 1e-9);
    }

    @Test
    void generationUnderBudget() {
        long start = System.nanoTime();
        TerrainGenerator.generate(789L, CFG);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs < 1500, "generation took " + elapsedMs + " ms");
    }

    @Test
    void terrainBaseValidation() {
        assertThrows(NullPointerException.class, () ->
                new TerrainBase(null, new FakeWater(), null, null, null));
        assertThrows(NullPointerException.class, () ->
                new TerrainBase(new FakeHeight(), new FakeWater(), null, null, new double[]{0, 1, 0}));
        assertThrows(IllegalArgumentException.class, () ->
                new TerrainBase(new FakeHeight(), new FakeWater(),
                        new FloatGrid(1, 1), new FloatGrid(1, 1), new double[]{1.0, 0.0}));
        assertThrows(IllegalArgumentException.class, () ->
                new TerrainBase(new FakeHeight(), new FakeWater(),
                        new FloatGrid(1, 1), new FloatGrid(1, 1), new double[]{Double.NaN, 0, 0}));
    }

    @Test
    void sunDirectionIsCloned() {
        double[] original = {0.0, 1.0, 0.0};
        TerrainBase t = new TerrainBase(new FakeHeight(), new FakeWater(),
                new FloatGrid(1, 1), new FloatGrid(1, 1), original);
        original[0] = 99.0;
        assertEquals(0.0, t.sunDirection()[0], "sunDirection should be cloned");
    }

    static class FakeHeight implements HeightField {
        public double size() { return 2000.0; }
        public double heightAt(double x, double z) { return 0.0; }
    }

    static class FakeWater implements WaterField {
        public double depthAt(double x, double z) { return 0.0; }
        public Vec2 flowAt(double x, double z) { return new Vec2(0, 0); }
        public double swampAt(double x, double z) { return 0.0; }
    }
}

