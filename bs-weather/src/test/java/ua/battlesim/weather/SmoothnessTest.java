package ua.battlesim.weather;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.WeatherState;

/**
 * Smooth transitions: with a 40 s blending time constant and 0.1 s ticks,
 * per-tick changes are bounded by the blend step, except that the 1%
 * convergence cutoff may snap a channel the last ≤ 1% to its target.
 */
class SmoothnessTest {

    @Test
    void channelChangesAreBoundedPerTick() {
        SimpleWeatherModel m = new SimpleWeatherModel(99L, true);
        WeatherState prev = m.state();
        for (int tick = 0; tick <= 30_000; tick++) {
            m.step(tick);
            WeatherState cur = m.state();
            // Per-tick changes are bounded by the 40 s blend step or the 1%
            // convergence cutoff (whichever is larger).
            assertTrue(Math.abs(cur.rain() - prev.rain()) <= 0.011, "tick " + tick
                    + " rain jumped " + (cur.rain() - prev.rain()));
            assertTrue(Math.abs(cur.heavyRain() - prev.heavyRain()) <= 0.011, "tick " + tick
                    + " heavyRain jumped " + (cur.heavyRain() - prev.heavyRain()));
            assertTrue(Math.abs(cur.snow() - prev.snow()) <= 0.011, "tick " + tick
                    + " snow jumped " + (cur.snow() - prev.snow()));
            assertTrue(Math.abs(cur.cloud() - prev.cloud()) <= 0.011, "tick " + tick
                    + " cloud jumped " + (cur.cloud() - prev.cloud()));
            assertTrue(Math.abs(cur.windSpeed() - prev.windSpeed()) <= 0.062,
                    "tick " + tick + " windSpeed jumped " + (cur.windSpeed() - prev.windSpeed()));
            double dAngle = Math.abs(cur.windDirection() - prev.windDirection());
            assertTrue(dAngle <= 0.011, "tick " + tick
                    + " windDirection jumped " + dAngle);
            assertTrue(Math.abs(cur.wetness() - prev.wetness()) <= 0.011, "tick " + tick
                    + " wetness jumped " + (cur.wetness() - prev.wetness()));
            assertTrue(Math.abs(cur.puddles() - prev.puddles()) <= 0.011, "tick " + tick
                    + " puddles jumped " + (cur.puddles() - prev.puddles()));
            assertTrue(Math.abs(cur.snowCover() - prev.snowCover()) <= 0.011, "tick " + tick
                    + " snowCover jumped " + (cur.snowCover() - prev.snowCover()));
            prev = cur;
        }
    }

    @Test
    void channelsStayInsideTheValidRanges() {
        SimpleWeatherModel m = new SimpleWeatherModel(23L, true);
        for (int tick = 0; tick <= 30_000; tick++) {
            m.step(tick);
            WeatherState s = m.state();
            assertTrue(s.rain() >= 0.0 && s.rain() <= 1.0, "tick " + tick + " rain " + s.rain());
            assertTrue(s.heavyRain() >= 0.0 && s.heavyRain() <= 1.0,
                    "tick " + tick + " heavyRain " + s.heavyRain());
            assertTrue(s.snow() >= 0.0 && s.snow() <= 1.0, "tick " + tick + " snow " + s.snow());
            assertTrue(s.cloud() >= 0.0 && s.cloud() <= 1.0, "tick " + tick + " cloud " + s.cloud());
            assertTrue(s.windSpeed() >= 0.0 && s.windSpeed() <= 22.0 + 1e-9,
                    "tick " + tick + " windSpeed " + s.windSpeed());
            assertTrue(s.windDirection() >= -StrictMath.PI && s.windDirection() <= StrictMath.PI,
                    "tick " + tick + " windDirection " + s.windDirection());
            assertTrue(s.wetness() >= 0.0 && s.wetness() <= 1.0,
                    "tick " + tick + " wetness " + s.wetness());
            assertTrue(s.puddles() >= 0.0 && s.puddles() <= 1.0,
                    "tick " + tick + " puddles " + s.puddles());
            assertTrue(s.snowCover() >= 0.0 && s.snowCover() <= 1.0,
                    "tick " + tick + " snowCover " + s.snowCover());
        }
    }

    @Test
    void snowCoverRisesWhileSnowingAndDecaysAfter() {
        for (long seed : new long[] {31L, 37L, 41L}) {
            SimpleWeatherModel m = new SimpleWeatherModel(seed, true);
            double prev = -1.0;
            boolean sawSnow = false;
            boolean sawDecay = false;
            for (int tick = 0; tick <= 60_000; tick++) {
                m.step(tick);
                double cover = m.state().snowCover();
                if (cover > 0.0) {
                    sawSnow = true;
                }
                if (sawSnow && prev >= 0.0 && cover < prev) {
                    sawDecay = true;
                }
                prev = cover;
            }
            assertTrue(sawSnow, "seed " + seed + " never produced snow in 100 minutes");
            assertTrue(sawDecay, "seed " + seed + " snow cover never decayed after snowing");
        }
    }
}
