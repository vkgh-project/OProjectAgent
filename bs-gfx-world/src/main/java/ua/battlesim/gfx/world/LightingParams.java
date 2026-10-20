package ua.battlesim.gfx.world;

import java.util.Objects;

/**
 * Lighting, atmosphere and weather-influenced parameters for one rendered
 * frame of the world.
 *
 * <p>Units: directions and colours are unit-less {@code [0, 1]} RGB / unit
 * vectors; {@code fogDensity} in {@code 1/m}; {@code timeSeconds} in seconds
 * (drives water and wind animation); {@code snow}/{@code wetness}/
 * {@code puddles} as {@code [0, 1]} factors; {@code windSpeed} in m/s and
 * {@code windDirection} in radians in the (x, z) plane.
 *
 * <p>Immutable and thread-safe; all values are validated on construction.
 */
public record LightingParams(
        Vec3 sunDirection,
        Vec3 sunColor,
        Vec3 ambientColor,
        Vec3 fogColor,
        float fogDensity,
        float timeSeconds,
        float snow,
        float wetness,
        float puddles,
        float windSpeed,
        float windDirection) {

    public LightingParams {
        Objects.requireNonNull(sunDirection, "sunDirection");
        Objects.requireNonNull(sunColor, "sunColor");
        Objects.requireNonNull(ambientColor, "ambientColor");
        Objects.requireNonNull(fogColor, "fogColor");
        requireFinite(fogDensity, "fogDensity");
        requireFinite(timeSeconds, "timeSeconds");
        requireFinite(windSpeed, "windSpeed");
        requireFinite(windDirection, "windDirection");
        if (fogDensity < 0.0f) {
            throw new IllegalArgumentException("fogDensity must be >= 0: " + fogDensity);
        }
        if (windSpeed < 0.0f) {
            throw new IllegalArgumentException("windSpeed must be >= 0: " + windSpeed);
        }
        snow = clamp01(snow);
        wetness = clamp01(wetness);
        puddles = clamp01(puddles);
    }

    /**
     * @return a clear-sky, dry, calm daylight default suitable for a demo.
     */
    public static LightingParams defaults() {
        return new LightingParams(
                new Vec3(0.5f, 0.8f, 0.3f),
                new Vec3(1.0f, 0.95f, 0.85f),
                new Vec3(0.35f, 0.40f, 0.45f),
                new Vec3(0.62f, 0.72f, 0.82f),
                0.0004f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f,
                0.0f);
    }

    /** @param timeSeconds the render time in seconds */
    public LightingParams withTime(float timeSeconds) {
        return new LightingParams(sunDirection, sunColor, ambientColor, fogColor,
                fogDensity, timeSeconds, snow, wetness, puddles, windSpeed, windDirection);
    }

    /**
     * @param snow    snow cover factor in {@code [0, 1]}
     * @param wetness wet-dirt darkening factor in {@code [0, 1]}
     * @param puddles  puddle factor in {@code [0, 1]}
     * @return a copy with the given weather-influenced surface factors
     */
    public LightingParams withWeather(double snow, double wetness, double puddles) {
        return new LightingParams(sunDirection, sunColor, ambientColor, fogColor,
                fogDensity, timeSeconds, (float) snow, (float) wetness, (float) puddles,
                windSpeed, windDirection);
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    private static float clamp01(float v) {
        if (Float.isNaN(v)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}