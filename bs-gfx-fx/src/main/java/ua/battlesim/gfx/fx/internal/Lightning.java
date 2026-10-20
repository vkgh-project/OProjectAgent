package ua.battlesim.gfx.fx.internal;

import ua.battlesim.api.WeatherState;

import java.util.Objects;

/**
 * The deterministic lightning flash schedule for heavy rain.
 *
 * <p>Flash events are a pure function of the simulated time, the heavy-rain
 * intensity and a fixed seed: time is divided into fixed windows and each
 * window either contains one flash (probability growing with the heavy-rain
 * intensity) or none; the flash envelope has a fast attack and an
 * exponential decay. Because only simulated seconds are used, pause and
 * playback speed are respected automatically.
 *
 * <p>No OpenGL, fully testable.
 */
public final class Lightning {

    /** The fixed seed shared by all renderers (deterministic schedule). */
    public static final long DEFAULT_SEED = 0x9E3779B97F4A7C15L;

    /** The length of one schedule window in simulated seconds. */
    public static final double WINDOW_SECONDS = 0.7;

    /** How long a flash lives after its start in simulated seconds. */
    public static final double FLASH_MAX_AGE_SECONDS = 0.55;

    /** Flash attack (rise) time in simulated seconds. */
    public static final double ATTACK_SECONDS = 0.025;

    /** Flash exponential decay constant in simulated seconds. */
    public static final double DECAY_SECONDS = 0.16;

    private Lightning() {
    }

    /**
     * The lightning flash intensity at the given simulated time.
     *
     * @param weather      the weather state (non-null); only {@code heavyRain} matters
     * @param timeSeconds  simulated seconds, finite and {@code >= 0}
     * @return the flash intensity in {@code [0, 1]} (0 when no heavy rain)
     */
    public static double flash(WeatherState weather, double timeSeconds) {
        return flash(weather, timeSeconds, DEFAULT_SEED);
    }

    /**
     * The lightning flash intensity at the given simulated time for a
     * specific seed.
     *
     * @param weather      the weather state (non-null)
     * @param timeSeconds  simulated seconds, finite and {@code >= 0}
     * @param seed         the schedule seed
     * @return the flash intensity in {@code [0, 1]}
     */
    public static double flash(WeatherState weather, double timeSeconds, long seed) {
        Objects.requireNonNull(weather, "weather");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("timeSeconds must be finite and >= 0: " + timeSeconds);
        }
        double heavy = weather.heavyRain();
        if (heavy <= 0.0) {
            return 0.0;
        }
        double probability = 0.25 + 0.55 * heavy;
        long window = (long) StrictMath.floor(timeSeconds / WINDOW_SECONDS);
        // Check the current window and, for the decay tail, the previous one.
        for (int k = 0; k <= 1; k++) {
            long w = window - k;
            double u = hash01(w, seed);
            if (u < probability) {
                double start = w * WINDOW_SECONDS + u * WINDOW_SECONDS;
                double age = timeSeconds - start;
                if (age >= 0.0 && age <= FLASH_MAX_AGE_SECONDS) {
                    double envelope = age < ATTACK_SECONDS
                            ? age / ATTACK_SECONDS
                            : StrictMath.exp(-(age - ATTACK_SECONDS) / DECAY_SECONDS);
                    return StrictMath.min(1.0, envelope) * (0.55 + 0.45 * heavy);
                }
            }
        }
        return 0.0;
    }

    private static double hash01(long n, long seed) {
        long h = n * 0x9E3779B97F4A7C15L + seed;
        h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
        h = (h ^ (h >>> 33)) * 0xC4CEB9FE1A85EC53L;
        h = h ^ (h >>> 33);
        return (h >>> 11) * (1.0 / 9007199254740992.0);
    }
}
