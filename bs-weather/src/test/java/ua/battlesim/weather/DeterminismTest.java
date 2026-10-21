package ua.battlesim.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.WeatherState;

/** Determinism: same seed -> same states; different seeds diverge; golden seed. */
class DeterminismTest {

    /**
     * Golden-seed constant: FNV-1a 64-bit state hash of seed 1234 at tick
     * 3600 (captured from the deterministic model; changes only if the model
     * itself changes).
     */
    private static final long GOLDEN_HASH_SEED_1234_TICK_3600 = -3188434249984907795L;

    @Test
    void sameSeedProducesIdenticalStates() {
        SimpleWeatherModel a = new SimpleWeatherModel(42L, true);
        SimpleWeatherModel b = new SimpleWeatherModel(42L, true);
        for (int tick = 0; tick <= 5000; tick++) {
            a.step(tick);
            b.step(tick);
            if (tick % 250 == 0) {
                assertEquals(a.state(), b.state(), "tick " + tick);
            }
        }
        assertEquals(a.state(), b.state());
        assertEquals(a.speedFactor(1.0, 0.0), b.speedFactor(1.0, 0.0));
        assertEquals(a.speedFactor(0.0, 1.0), b.speedFactor(0.0, 1.0));
        assertEquals(a.visibilityMetres(), b.visibilityMetres());
        assertEquals(a.combatFactor(), b.combatFactor());
        assertEquals(a.conditions(), b.conditions());
    }

    @Test
    void differentSeedsProduceDifferentStates() {
        SimpleWeatherModel a = new SimpleWeatherModel(1L, true);
        SimpleWeatherModel b = new SimpleWeatherModel(2L, true);
        boolean differs = false;
        for (int tick = 0; tick <= 10000 && !differs; tick++) {
            a.step(tick);
            b.step(tick);
            if (tick % 100 == 0) {
                differs = !a.state().equals(b.state())
                        || a.speedFactor(1.0, 0.0) != b.speedFactor(1.0, 0.0);
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(differs,
                "different seeds must eventually produce different weather");
    }

    @Test
    void reSteppingTheSameTickIsANoop() {
        SimpleWeatherModel a = new SimpleWeatherModel(7L, true);
        SimpleWeatherModel b = new SimpleWeatherModel(7L, true);
        for (int tick = 0; tick <= 50; tick++) {
            a.step(tick);
            b.step(tick);
        }
        b.step(50); // duplicate step must not advance
        for (int tick = 51; tick <= 200; tick++) {
            a.step(tick);
            b.step(tick);
        }
        assertEquals(a.state(), b.state());
    }

    @Test
    void goldenSeedStateHash() {
        SimpleWeatherModel m = new SimpleWeatherModel(1234L, true);
        for (int tick = 0; tick <= 3600; tick++) {
            m.step(tick);
        }
        long hash = stateHash(m.state());
        assertEquals(GOLDEN_HASH_SEED_1234_TICK_3600, hash);
    }

    /** FNV-1a 64-bit over the bit patterns of all nine state channels. */
    static long stateHash(WeatherState s) {
        long h = 0xcbf29ce484222325L;
        h = mix(h, s.rain());
        h = mix(h, s.heavyRain());
        h = mix(h, s.snow());
        h = mix(h, s.windSpeed());
        h = mix(h, s.windDirection());
        h = mix(h, s.cloud());
        h = mix(h, s.wetness());
        h = mix(h, s.puddles());
        h = mix(h, s.snowCover());
        return h;
    }

    private static long mix(long h, double v) {
        long bits = Double.doubleToLongBits(v);
        for (int i = 0; i < 8; i++) {
            h ^= (bits >>> (8 * i)) & 0xffL;
            h *= 0x100000001b3L;
        }
        return h;
    }
}
