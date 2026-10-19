package ua.battlesim.api;

import static java.lang.Double.isFinite;

/**
 * Immutable weather state of one tick.
 *
 * <p>Units: intensities in {@code [0, 1]}; windSpeed in m/s; windDirection
 * in radians (direction the wind blows toward, in the (x, z) plane, x east,
 * z south); wetness, puddles and snowCover in {@code [0, 1]}.
 *
 * <p>Immutable and thread-safe; safe to deliver to the render thread.
 */
public record WeatherState(double rain, double heavyRain, double snow, double windSpeed,
        double windDirection, double cloud, double wetness, double puddles, double snowCover) {

    public WeatherState {
        checkUnitInterval("rain", rain);
        checkUnitInterval("heavyRain", heavyRain);
        checkUnitInterval("snow", snow);
        if (!isFinite(windSpeed) || windSpeed < 0.0) {
            throw new IllegalArgumentException("windSpeed must be finite and >= 0 (m/s): " + windSpeed);
        }
        if (!isFinite(windDirection)) {
            throw new IllegalArgumentException("windDirection must be finite (radians): " + windDirection);
        }
        checkUnitInterval("cloud", cloud);
        checkUnitInterval("wetness", wetness);
        checkUnitInterval("puddles", puddles);
        checkUnitInterval("snowCover", snowCover);
    }

    private static void checkUnitInterval(String name, double v) {
        if (!isFinite(v) || v < 0.0 || v > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0,1]: " + v);
        }
    }
}
