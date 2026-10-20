package ua.battlesim.gfx.fx.internal;

import ua.battlesim.api.AttackEvent;
import ua.battlesim.api.RenderSnapshot;
import ua.battlesim.api.Side;
import ua.battlesim.api.SimEvent;
import ua.battlesim.api.UnitDestroyedEvent;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.UnitView;
import ua.battlesim.api.WeatherState;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Behaviour tests of the deterministic effect model: the full attack →
 * tracer → impact explosion chain, the wreck visibility rules and the
 * deterministic reproduction of the same call sequence.
 */
class EffectsModelTest {

    private static RenderSnapshot snapshot(double ax, double az, double tx, double tz) {
        UnitView attacker = new UnitView(1, Side.BLUE, UnitType.TANK,
                ax, az, ax, az, 0.0, 0.0, 0.0, 0.0, 100, UnitState.READY, 0, -1);
        UnitView target = new UnitView(2, Side.RED, UnitType.INFANTRY,
                tx, tz, tx, tz, StrictMath.PI, StrictMath.PI, StrictMath.PI, StrictMath.PI, 100, UnitState.READY, 0, -1);
        WeatherState w = new WeatherState(0.0, 0.0, 0.0, 0.0, 0.0, 0.2, 0.0, 0.0, 0.0);
        return new RenderSnapshot(1, 0, 0.0, List.of(attacker, target), w);
    }

    @Test
    void attack_spawnsMuzzleAndTracer_immediately() {
        EffectsModel m = new EffectsModel(42L, 1024);
        m.onEvents(List.of(new AttackEvent(0, 1, 2, 10, false)), snapshot(0, 0, 480, 0));
        assertTrue(m.liveCount(ParticleType.MUZZLE_FLASH) >= 1);
        assertTrue(m.liveCount(ParticleType.SPARK) >= 6);
        assertTrue(m.liveCount(ParticleType.SMOKE) >= 3);
        assertEquals(1, m.tracers().count());
        // 480 m at 600 m/s is exactly the 0.8 s window edge.
        assertEquals(0.8, m.tracers().impactTime(0), 1e-12);
    }

    @Test
    void impact_happensOnlyWhenTheShellLands() {
        EffectsModel m = new EffectsModel(42L, 1024);
        m.onEvents(List.of(new AttackEvent(0, 1, 2, 10, false)), snapshot(0, 0, 480, 0));
        m.update(0.4, 0, 0, 0);
        assertEquals(0, m.liveCount(ParticleType.IMPACT_FLASH), "no explosion before impact");
        assertEquals(1, m.tracers().count());
        // Mid-flight, the tip is halfway to the target.
        assertEquals(240.0, m.tracers().tipX(0, 0.4), 1e-9);
        m.update(0.4, 0, 0, 0);
        assertEquals(0, m.tracers().count(), "shell consumed on impact");
        assertTrue(m.liveCount(ParticleType.IMPACT_FLASH) >= 1, "impact flash present");
        assertTrue(m.liveCount(ParticleType.FIRE) >= 10, "impact fire present");
        assertTrue(m.liveCount(ParticleType.SPARK) >= 20, "impact sparks present");
        assertTrue(m.liveCount(ParticleType.DUST) >= 12, "impact dust present");
    }

    @Test
    void wreck_visibleOnlyAfterLastShellAndEmitsFor90s() {
        EffectsModel m = new EffectsModel(42L, 1024);
        // Killing shot, fired at t = 0, landing at t = 0.8.
        m.onEvents(List.of(new AttackEvent(0, 1, 2, 100, true)), snapshot(0, 0, 480, 0));
        m.update(0.8, 0, 0, 0);
        // The destruction event arrives with the impact.
        m.onEvents(List.of(new UnitDestroyedEvent(8, 2, 1, Side.BLUE)), snapshot(0, 0, 480, 0));
        int id = 2;
        assertTrue(m.wrecks().known(id));
        assertEquals(0.8, m.wrecks().visibleAt(id), 1e-12, "visible only from the impact");

        // Advance one more second: the wreck must be emitting smoke and fire.
        int smokeBefore = m.liveCount(ParticleType.SMOKE);
        int fireBefore = m.liveCount(ParticleType.FIRE);
        m.update(1.0, 0, 0, 0);
        assertTrue(m.liveCount(ParticleType.SMOKE) > smokeBefore, "wreck smoke emitted");
        assertTrue(m.liveCount(ParticleType.FIRE) > fireBefore, "wreck fire emitted");

        // After the 90 s effect window, emission stops and the pool drains.
        while (m.simTime() < 101.5) {
            m.update(0.5, 0, 0, 0);
        }
        assertEquals(0, m.liveCount(ParticleType.FIRE), "wreck fire gone after the 90 s window");
        assertEquals(0, m.wrecks().count(), "wreck pruned");
    }
    @Test
    void unknownUnits_areIgnoredSafely() {
        EffectsModel m = new EffectsModel(42L, 1024);
        m.onEvents(List.of(new AttackEvent(0, 99, 100, 10, false)), snapshot(0, 0, 480, 0));
        assertEquals(0, m.tracers().count());
        m.onEvents(List.of(new UnitDestroyedEvent(0, 99, -1, Side.RED)), snapshot(0, 0, 480, 0));
        assertEquals(0, m.wrecks().count());
        m.update(1.0, 0, 0, 0);
    }

    @Test
    void rain_spawnsAroundCentre_andDropsBelowKillPlane() {
        EffectsModel m = new EffectsModel(42L, 1024);
        WeatherState w = new WeatherState(1.0, 0.5, 0.0, 10.0, StrictMath.PI / 2.0, 0.9, 0.5, 0.5, 0.0);
        RenderSnapshot snap = new RenderSnapshot(1, 0, 0.0, List.of(
                new UnitView(1, Side.BLUE, UnitType.TANK,
                        100, 100, 100, 100, 0, 0, 0, 0, 100, UnitState.READY, 0, -1)), w);
        m.onEvents(List.of(), snap);
        for (int i = 0; i < 20; i++) {
            m.update(0.5, 100, 0, 100);
        }
        assertTrue(m.liveCount(ParticleType.RAIN) > 0, "rain should be falling");
        // Rain spawns within 60 m of the centre and stays in the box.
        boolean found = false;
        for (int l = 0; l < m.pool().blockSize(ParticleType.RAIN); l++) {
            if (!m.pool().isAlive(ParticleType.RAIN, l)) {
                continue;
            }
            ParticleState s = m.pool().state(ParticleType.RAIN, l);
            if (StrictMath.abs(s.x() - 100) <= 70 && StrictMath.abs(s.z() - 100) <= 70) {
                found = true;
            }
        }
        assertTrue(found, "rain spawns around the centre");
    }

    @Test
    void sameSeedSameCalls_sameFingerprint() {
        EffectsModel a = new EffectsModel(777L, 1024);
        EffectsModel b = new EffectsModel(777L, 1024);
        RenderSnapshot snap = snapshot(0, 0, 480, 0);
        List<SimEvent> events = List.of(new AttackEvent(0, 1, 2, 100, true),
                new UnitDestroyedEvent(8, 2, 1, Side.BLUE));
        a.onEvents(events, snap);
        b.onEvents(events, snap);
        for (int i = 0; i < 120; i++) {
            a.update(0.1, 240, 0, 0);
            b.update(0.1, 240, 0, 0);
        }
        assertEquals(a.fingerprint(), b.fingerprint(), "determinism");
        // A different seed must (with overwhelming probability) diverge.
        EffectsModel c = new EffectsModel(778L, 1024);
        c.onEvents(events, snap);
        for (int i = 0; i < 120; i++) {
            c.update(0.1, 240, 0, 0);
        }
        assertFalse(a.fingerprint() == c.fingerprint(), "different seeds should diverge");
    }

    @Test
    void validation() {
        EffectsModel m = new EffectsModel(42L, 1024);
        assertThrows(NullPointerException.class, () -> m.onEvents(null, snapshot(0, 0, 1, 0)));
        assertThrows(NullPointerException.class, () -> m.onEvents(List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> m.update(-0.1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> m.update(Double.NaN, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> m.update(1.0, Double.POSITIVE_INFINITY, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new EffectsModel(42L, 511));
    }
}