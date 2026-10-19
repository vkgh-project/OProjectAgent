package ua.battlesim.weather;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.WeatherState;

/**
 * Exclusivity: the precipitation regime is single. The active channel may be
 * joined by the fading previous channel and, right after a regime change, by
 * the one before that; a channel three regime changes old (at least 240 s
 * old) is at most {@code e^{-6} < 0.01}, i.e. snapped to exactly zero. So at
 * most two channels are ever above 0.05, at most one is ever dominant, and
 * the cross-fade overlap of consecutive regimes is bounded.
 */
class ExclusivityTest {

    private static final int LAST_TICK = 90_000; // 150 minutes of weather

    @Test
    void atMostThreeChannelsAreEverNonZero() {
        for (long seed : new long[] {7L, 17L, 29L}) {
            List<WeatherState> states = record(seed, LAST_TICK);
            for (int t = 0; t < states.size(); t++) {
                WeatherState s = states.get(t);
                int active = 0;
                if (s.rain() > 0.0) {
                    active++;
                }
                if (s.heavyRain() > 0.0) {
                    active++;
                }
                if (s.snow() > 0.0) {
                    active++;
                }
                assertTrue(active <= 3, "seed " + seed + " tick " + t + ": " + active
                        + " precipitation channels non-zero: " + s);
            }
        }
    }

    @Test
    void atMostOneDominantChannelAndBoundedOverlap() {
        List<WeatherState> states = record(7L, LAST_TICK);
        for (int t = 0; t < states.size(); t++) {
            WeatherState s = states.get(t);
            int dominant = 0;
            if (s.rain() > 0.5) {
                dominant++;
            }
            if (s.heavyRain() > 0.5) {
                dominant++;
            }
            if (s.snow() > 0.5) {
                dominant++;
            }
            assertTrue(dominant <= 1, "tick " + t + ": " + dominant
                    + " dominant channels: " + s);

            // The active regime plus at most one fading previous regime may
            // be significant (a channel two switches old is <= e^{-6}).
            int significant = 0;
            if (s.rain() > 0.05) {
                significant++;
            }
            if (s.heavyRain() > 0.05) {
                significant++;
            }
            if (s.snow() > 0.05) {
                significant++;
            }
            assertTrue(significant <= 2, "tick " + t + ": " + significant
                    + " significant channels: " + s);

            // Bounded cross-fade overlap of consecutive regimes.
            double total = s.rain() + s.heavyRain() + s.snow();
            assertTrue(total <= 1.1, "tick " + t + ": precipitation total "
                    + total + " above 1.1: " + s);
        }
    }

    private static List<WeatherState> record(long seed, int lastTick) {
        SimpleWeatherModel m = new SimpleWeatherModel(seed, true);
        List<WeatherState> states = new java.util.ArrayList<>(lastTick + 1);
        states.add(m.state());
        for (int tick = 0; tick <= lastTick; tick++) {
            m.step(tick);
            states.add(m.state());
        }
        return List.copyOf(states);
    }
}
