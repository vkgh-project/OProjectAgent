package ua.battlesim.gfx.fx;

import ua.battlesim.api.WeatherState;

import java.util.Objects;

/**
 * The immutable sky and light palette derived from a {@link WeatherState}:
 * sky zenith and horizon colours, sun colour, the scene light colour and the
 * cloud colour plus coverage.
 *
 * <p>All colour components and {@code cloudAmount} are in {@code [0, 1]}.
 * The mapping is a pure, deterministic function of the weather state — no
 * time, no randomness — so it is trivially testable without a GPU.
 */
public record SkyColors(double topR, double topG, double topB,
        double horizonR, double horizonG, double horizonB,
        double sunR, double sunG, double sunB,
        double lightR, double lightG, double lightB,
        double cloudR, double cloudG, double cloudB,
        double cloudAmount) {

    public SkyColors {
        for (double v : new double[] { topR, topG, topB, horizonR, horizonG, horizonB,
                sunR, sunG, sunB, lightR, lightG, lightB, cloudR, cloudG, cloudB, cloudAmount }) {
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("SkyColors values must be finite, got: " + v);
            }
            if (v < 0.0 || v > 1.0) {
                throw new IllegalArgumentException("SkyColors values must be in [0,1]: " + v);
            }
        }
    }

    /**
     * Derives the palette from a weather state.
     *
     * @param weather the weather state (non-null)
     * @return the derived palette
     */
    public static SkyColors from(WeatherState weather) {
        Objects.requireNonNull(weather, "weather");
        double cloud = weather.cloud();
        double rain = Math.min(1.0, weather.rain() + weather.heavyRain());
        double snow = weather.snow();
        double storm = Math.min(1.0, 0.65 * cloud + 0.7 * rain);
        double snowy = Math.min(1.0, snow);
        double snowBlend = 0.6 * snowy;

        double topR = lerp(lerp(0.30, 0.16, storm), 0.55, snowBlend);
        double topG = lerp(lerp(0.52, 0.18, storm), 0.62, snowBlend);
        double topB = lerp(lerp(0.85, 0.24, storm), 0.74, snowBlend);

        double horizonR = lerp(lerp(0.66, 0.38, storm), 0.80, snowBlend);
        double horizonG = lerp(lerp(0.79, 0.42, storm), 0.84, snowBlend);
        double horizonB = lerp(lerp(0.92, 0.48, storm), 0.88, snowBlend);

        double dim = 1.0 - 0.6 * storm;
        double sunR = 1.0 * dim;
        double sunG = 0.93 * dim;
        double sunB = 0.78 * dim;

        double lightR = lerp(1.00, 0.45, storm);
        double lightG = lerp(0.95, 0.47, storm);
        double lightB = lerp(0.85, 0.52, storm);

        double cloudR = lerp(0.95, 0.42, storm);
        double cloudG = lerp(0.95, 0.44, storm);
        double cloudB = lerp(0.97, 0.50, storm);
        double amount = Math.min(1.0, cloud + 0.15 * rain);

        return new SkyColors(topR, topG, topB,
                horizonR, horizonG, horizonB,
                sunR, sunG, sunB,
                lightR, lightG, lightB,
                cloudR, cloudG, cloudB,
                amount);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}