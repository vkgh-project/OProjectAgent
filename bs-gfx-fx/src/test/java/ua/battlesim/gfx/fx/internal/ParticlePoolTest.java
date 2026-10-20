package ua.battlesim.gfx.fx.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Behaviour tests for the deterministic particle pool: per-type blocks, ring
 * overwrite, the per-type physical model and determinism.
 */
class ParticlePoolTest {

    private static final int CAPACITY = 512;

    @Test
    void defaultCapacity_isAbout24000() {
        assertEquals(24000, ParticlePool.DEFAULT_CAPACITY);
        ParticlePool pool = new ParticlePool(ParticlePool.DEFAULT_CAPACITY);
        int sum = 0;
        for (ParticleType type : ParticleType.values()) {
            sum += pool.blockSize(type);
        }
        assertEquals(24000, sum);
        for (ParticleType type : ParticleType.values()) {
            assertTrue(pool.blockSize(type) >= 8);
        }
    }

    @Test
    void capacityTooSmall_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new ParticlePool(511));
    }

    @Test
    void spawnAndLiveCount_perTypeIsolation() {
        ParticlePool pool = new ParticlePool(CAPACITY);
        pool.spawn(ParticleType.SMOKE, 0, 0, 0, 0, 1, 0, 2.0, 1.0, 0.5, 1, 1, 1);
        pool.spawn(ParticleType.FIRE, 1, 0, 1, 0, 1, 0, 1.0, 0.5, 1.0, 1, 0.5, 0);
        assertEquals(1, pool.liveCount(ParticleType.SMOKE));
        assertEquals(1, pool.liveCount(ParticleType.FIRE));
        assertEquals(0, pool.liveCount(ParticleType.DUST));
    }

    @Test
    void ringOverwrite_whenBlockFull() {
        ParticlePool pool = new ParticlePool(CAPACITY);
        int block = pool.blockSize(ParticleType.SPARK);
        // Fill the block exactly.
        for (int i = 0; i < block; i++) {
            pool.spawn(ParticleType.SPARK, i, 0, 0, 0, 0, 0, 10.0, 0.2, 1.0, 1, 1, 1);
        }
        assertEquals(block, pool.liveCount(ParticleType.SPARK));
        // One more spawn overwrites slot 0.
        pool.spawn(ParticleType.SPARK, 999, 999, 999, 0, 0, 0, 10.0, 0.2, 1.0, 1, 1, 1);
        assertEquals(block, pool.liveCount(ParticleType.SPARK));
        assertEquals(999.0, pool.state(ParticleType.SPARK, 0).x());
        // Slot 1 (second spawned) survived.
        assertEquals(1.0, pool.state(ParticleType.SPARK, 1).x());
    }

    @Test
    void update_killsExpired() {
        ParticlePool pool = new ParticlePool(CAPACITY);
        pool.spawn(ParticleType.SMOKE, 0, 0, 0, 0, 1, 0, 1.0, 1.0, 0.5, 1, 1, 1);
        pool.update(0.5, 0, 0, -100);
        assertTrue(pool.isAlive(ParticleType.SMOKE, 0));
        pool.update(0.5, 0, 0, -100);
        assertFalse(pool.isAlive(ParticleType.SMOKE, 0));
        assertEquals(0, pool.liveCount(ParticleType.SMOKE));
    }

    @Test
    void spark_fallsUnderGravity() {
        ParticlePool pool = new ParticlePool(CAPACITY);
        pool.spawn(ParticleType.SPARK, 0, 0, 0, 0, 0, 0, 10.0, 0.2, 1.0, 1, 1, 1);
        pool.update(1.0, 0, 0, -100);
        // Euler: vy after 1 s is -9.8, position moved by -9.8 m in that step.
        assertEquals(-9.8, pool.state(ParticleType.SPARK, 0).y(), 1e-6);
        assertEquals(-9.8, pool.state(ParticleType.SPARK, 0).vy(), 1e-6);
    }

    @Test
    void flash_growsAndFades() {
        ParticlePool pool = new ParticlePool(CAPACITY);
        pool.spawn(ParticleType.MUZZLE_FLASH, 0, 0, 0, 0, 0, 0, 1.0, 1.0, 1.0, 1, 1, 1);
        ParticleState half = pool.state(ParticleType.MUZZLE_FLASH, 0);
        pool.update(0.5, 0, 0, -100);
        ParticleState s = pool.state(ParticleType.MUZZLE_FLASH, 0);
        // size = base * (1 + 2 * 0.5) = 2.0; alpha = 1 * 0.5^2 = 0.25
        assertEquals(half.size() * 2.0, s.size(), 1e-6);
        assertEquals(0.25, s.alpha(), 1e-6);
    }

    @Test
    void rain_killedByKillPlane() {
        ParticlePool pool = new ParticlePool(CAPACITY);
        pool.spawn(ParticleType.RAIN, 0, 10.0, 0, 0, -10.0, 0, 5.0, 0.15, 0.4, 0.6, 0.6, 0.8);
        pool.update(1.0, 0, 0, 0.0);
        assertFalse(pool.isAlive(ParticleType.RAIN, 0), "rain crossed the kill plane");
    }

    @Test
    void windDriftsSmoke() {
        ParticlePool pool = new ParticlePool(CAPACITY);
        pool.spawn(ParticleType.SMOKE, 0, 0, 0, 0, 0.5, 0, 10.0, 1.0, 0.5, 1, 1, 1);
        pool.update(1.0, 10.0, 4.0, -100);
        ParticleState s = pool.state(ParticleType.SMOKE, 0);
        // wind factor 0.30: x drift = 10 * 0.3 * 1 = 3
        assertEquals(3.0, s.x(), 1e-6);
        assertEquals(1.2, s.z(), 1e-6);
    }

    @Test
    void twoPools_sameSequences_sameFingerprint() {
        ParticlePool a = new ParticlePool(CAPACITY);
        ParticlePool b = new ParticlePool(CAPACITY);
        for (int i = 0; i < 50; i++) {
            a.spawn(ParticleType.SMOKE, i, i * 0.5, i * 0.25, 0, 0.5, 0, 3.0 + i * 0.01, 1.0 + i * 0.01, 0.5, 0.3, 0.3, 0.3);
            b.spawn(ParticleType.SMOKE, i, i * 0.5, i * 0.25, 0, 0.5, 0, 3.0 + i * 0.01, 1.0 + i * 0.01, 0.5, 0.3, 0.3, 0.3);
            a.update(0.1, 1.0, 2.0, -10);
            b.update(0.1, 1.0, 2.0, -10);
        }
        assertEquals(a.fingerprint(), b.fingerprint());
    }

    @Test
    void validation() {
        ParticlePool pool = new ParticlePool(CAPACITY);
        assertThrows(IllegalArgumentException.class, () ->
                pool.spawn(ParticleType.SMOKE, Double.NaN, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () ->
                pool.spawn(ParticleType.SMOKE, 0, 0, 0, 0, 0, 0, -1, 1, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () ->
                pool.spawn(ParticleType.SMOKE, 0, 0, 0, 0, 0, 0, 1, 1, 1.5, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> pool.update(-0.1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> pool.update(Double.NaN, 0, 0, 0));
    }
}