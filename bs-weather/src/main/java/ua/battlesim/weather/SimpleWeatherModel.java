package ua.battlesim.weather;

import java.util.List;

import ua.battlesim.api.WeatherModel;
import ua.battlesim.api.WeatherState;
import ua.battlesim.math.DetRandom;
import ua.battlesim.weather.internal.Precip;

/**
 * Deterministic per-round weather model (bs-api {@link WeatherModel}).
 *
 * <p>Behaviour: the precipitation regime is exclusive ({@code CLEAR},
 * {@code RAIN}, {@code HEAVY_RAIN}, {@code SNOW}); every 2–6 minutes a new
 * random regime and new wind targets are drawn from a seeded
 * {@link DetRandom}. All channels (rain, heavy rain, snow, cloud, wind speed
 * and wind direction) approach their targets with a 40 s time constant
 * (exponential blending), so transitions are smooth. Wetness and puddles
 * rise while any precipitation is active and decay otherwise; snow cover
 * rises while it snows, decays otherwise and melts faster when clear.
 * Strong wind (12–22 m/s) is drawn independently of the precipitation
 * regime.
 *
 * <p>Formulas (per the module spec):
 * <ul>
 * <li>{@code speedFactor(dir) = 1 - 0.08 rain - 0.22 heavy - 0.18 snow
 *     - 0.10 snowCover - 0.05 puddles + 0.004 x tailwind}, clamped to
 *     {@code [0.4, 1.1]};</li>
 * <li>{@code visibilityMetres() = 25000 / (1 + 5 rain + 24 heavy + 10 snow)} metres;</li>
 * <li>{@code combatFactor = clamp(visibilityMetres() / 5000, 0.3, 1)}.</li>
 * </ul>
 *
 * <p>When constructed with {@code enabled = false} the weather is
 * permanently clear with a 2 m/s wind blowing toward +x (east).
 *
 * <p>Determinism: a pure function of the seed and the tick sequence;
 * {@link DetRandom} is the only randomness source, {@link StrictMath} is
 * used for all transcendental calls, and there is no wall clock, no static
 * mutable state and no hash-order dependence.
 *
 * <p>Thread-safety: mutable; call from the single simulation worker thread
 * ({@link #step} before movement and combat of a tick); {@link #state()} and
 * the factor methods may then be read by the render thread.
 */
public final class SimpleWeatherModel implements WeatherModel {

    /** Standard simulation step: time = tick x 0.1 s. */
    public static final double DEFAULT_STEP_SECONDS = 0.1;

    /** Blending time constant: 40 s. */
    private static final double BLEND_TAU_SECONDS = 40.0;

    /**
     * Convergence cutoff: channels within 1% of their target snap to it, so
     * a regime that loses the target is exactly zero within about 2 minutes
     * (keeping the blending time constant 40 s while the exponential tail is
     * cut off where it is no longer visible).
     */
    private static final double SNAP = 1e-2;

    private static final double TWO_PI = 2.0 * StrictMath.PI;

    /** Strong wind band: 12–22 m/s. */
    private static final double STRONG_WIND_MIN = 12.0;
    private static final double STRONG_WIND_MAX = 22.0;

    /** New random targets every 2–6 minutes. */
    private static final double NEXT_CHANGE_MIN_SECONDS = 120.0;
    private static final double NEXT_CHANGE_SPAN_SECONDS = 240.0;

    /** Snow cover rises about 200 s to full while it snows (per 0.1 s tick). */
    private static final double SNOW_COVER_GROWTH_PER_TICK = 0.0005;

    /** Per-tick (0.1 s) snow-cover decay factors (clear melts faster). */
    private static final double SNOW_COVER_DECAY_CLEAR_PER_TICK = 0.010;
    private static final double SNOW_COVER_DECAY_OTHER_PER_TICK = 0.003;

    private final boolean enabled;

    /** Simulated seconds per tick (time = tick x stepSeconds). */
    private final double stepSeconds;

    /** Per-tick approach factor {@code 1 - exp(-stepSeconds / 40)}. */
    private final double step;

    /** Time scale relative to the 0.1 s reference tick (1.0 at the default). */
    private final double timeScale;

    private final DetRandom rng;

    /** Current precipitation regime (the active target). */
    private Precip precip;

    /** Current channel values in [0, 1] (wind speed in m/s, direction in rad). */
    private double rain;
    private double heavyRain;
    private double snow;
    private double cloud;
    private double windSpeed;
    private double windDirection;
    private double wetness;
    private double puddles;
    private double snowCover;

    /** Current targets. */
    private double rainTarget;
    private double heavyTarget;
    private double snowTarget;
    private double cloudTarget;
    private double windTarget;
    private double windDirectionTarget;

    /** Simulation time (seconds) at which the next targets are drawn. */
    private double seconds;
    private double nextChangeSeconds;

    /** Last tick passed to {@link #step(int)}; -1 before the first step. */
    private int lastTick = -1;

    /**
     * @param seed    the deterministic seed; the same seed gives the same
     *                weather sequence
     * @param enabled {@code false} keeps the weather permanently clear with a
     *                2 m/s wind; {@code true} enables the random evolution
     */
    public SimpleWeatherModel(long seed, boolean enabled) {
        this(seed, enabled, DEFAULT_STEP_SECONDS);
    }

    /**
     * @param seed         the deterministic seed; the same seed gives the same
     *                     weather sequence
     * @param enabled      {@code false} keeps the weather permanently clear
     *                     with a 2 m/s wind; {@code true} enables the random
     *                     evolution
     * @param stepSeconds  simulated seconds per tick, finite and {@code > 0}
     *                     (all time-dependent rates scale with it)
     * @throws IllegalArgumentException if {@code stepSeconds} is not finite
     *         and positive
     */
    public SimpleWeatherModel(long seed, boolean enabled, double stepSeconds) {
        if (!Double.isFinite(stepSeconds) || stepSeconds <= 0.0) {
            throw new IllegalArgumentException("stepSeconds must be finite and > 0: " + stepSeconds);
        }
        this.enabled = enabled;
        this.stepSeconds = stepSeconds;
        this.step = 1.0 - StrictMath.exp(-stepSeconds / BLEND_TAU_SECONDS);
        this.timeScale = stepSeconds / DEFAULT_STEP_SECONDS;
        this.rng = new DetRandom(seed);
        // Neutral, clear baseline.
        this.precip = Precip.CLEAR;
        this.rain = 0.0;
        this.heavyRain = 0.0;
        this.snow = 0.0;
        this.cloud = 0.1;
        this.windSpeed = 5.0;
        this.windDirection = 0.0;
        this.wetness = 0.0;
        this.puddles = 0.0;
        this.snowCover = 0.0;
        if (enabled) {
            // First targets (possibly non-clear) and the first regime change
            // after 2–6 minutes.
            this.seconds = 0.0;
            this.nextChangeSeconds = scheduleNextChange();
            this.drawTargets();
        } else {
            // Permanently clear, 2 m/s wind toward +x (east).
            this.cloud = 0.0;
            this.windSpeed = 2.0;
            this.rainTarget = 0.0;
            this.heavyTarget = 0.0;
            this.snowTarget = 0.0;
            this.cloudTarget = 0.0;
            this.windTarget = 2.0;
            this.windDirectionTarget = 0.0;
            this.seconds = 0.0;
            this.nextChangeSeconds = Double.POSITIVE_INFINITY;
        }
    }

    /** Draws a new precipitation regime and wind targets from {@code rng}. */
    private void drawTargets() {
        precip = Precip.values()[rng.nextInt(Precip.values().length)];
        switch (precip) {
            case CLEAR -> {
                rainTarget = 0.0;
                heavyTarget = 0.0;
                snowTarget = 0.0;
                cloudTarget = 0.10 + 0.15 * rng.nextDouble();
            }
            case RAIN -> {
                rainTarget = 0.40 + 0.60 * rng.nextDouble();
                heavyTarget = 0.0;
                snowTarget = 0.0;
                cloudTarget = 0.55 + 0.35 * rng.nextDouble();
            }
            case HEAVY_RAIN -> {
                rainTarget = 0.0;
                heavyTarget = 0.50 + 0.50 * rng.nextDouble();
                snowTarget = 0.0;
                cloudTarget = 0.80 + 0.20 * rng.nextDouble();
            }
            case SNOW -> {
                rainTarget = 0.0;
                heavyTarget = 0.0;
                snowTarget = 0.30 + 0.70 * rng.nextDouble();
                cloudTarget = 0.65 + 0.35 * rng.nextDouble();
            }
            default -> throw new IllegalStateException("unreachable");
        }
        // Strong wind is independent of the precipitation regime.
        if (rng.nextInt(2) == 0) {
            windTarget = STRONG_WIND_MIN + (STRONG_WIND_MAX - STRONG_WIND_MIN) * rng.nextDouble();
        } else {
            windTarget = 1.0 + 10.0 * rng.nextDouble();
        }
        windDirectionTarget = normalizeAngle(rng.nextDouble() * TWO_PI);
    }

    /** @return the time (seconds) of the next target draw, 2–6 minutes out */
    private double scheduleNextChange() {
        return seconds + NEXT_CHANGE_MIN_SECONDS + NEXT_CHANGE_SPAN_SECONDS * rng.nextDouble();
    }

    @Override
    public void step(int tick) {
        if (!enabled) {
            // Permanently clear: nothing to do.
            lastTick = Math.max(lastTick, tick);
            return;
        }
        if (tick <= lastTick) {
            // Re-stepping a tick is a no-op; only strictly forward ticks advance.
            return;
        }
        lastTick = tick;
        seconds += stepSeconds;
        if (seconds >= nextChangeSeconds) {
            nextChangeSeconds = scheduleNextChange();
            drawTargets();
        }
        // Exponential approach of every channel to its target.
        rain = approach(rain, rainTarget);
        heavyRain = approach(heavyRain, heavyTarget);
        snow = approach(snow, snowTarget);
        cloud = approach(cloud, cloudTarget);
        windSpeed = approach(windSpeed, windTarget);
        windDirection = approachAngle(windDirection, windDirectionTarget);
        // Ground effects: rise while precipitating, decay otherwise.
        double precipitation = Math.max(rain, heavyRain) + snow;
        double wetnessTarget = precipitation > 0.0 ? Math.min(1.0, 0.3 + precipitation) : 0.0;
        double puddleTarget = precipitation > 0.0
                ? Math.min(1.0, precipitation * (0.5 + 0.5 * precipitation))
                : 0.0;
        wetness = approach(wetness, wetnessTarget);
        puddles = approach(puddles, puddleTarget);
        if (precip == Precip.SNOW) {
            snowCover = Math.min(1.0, snowCover + SNOW_COVER_GROWTH_PER_TICK * timeScale);
        } else {
            double decay = (precip == Precip.CLEAR ? SNOW_COVER_DECAY_CLEAR_PER_TICK
                    : SNOW_COVER_DECAY_OTHER_PER_TICK) * timeScale;
            snowCover = Math.max(0.0, snowCover * (1.0 - decay));
        }
    }

    @Override
    public WeatherState state() {
        return new WeatherState(rain, heavyRain, snow, windSpeed, windDirection,
                cloud, wetness, puddles, snowCover);
    }

    @Override
    public double speedFactor(double dirX, double dirZ) {
        if (!enabled) {
            // Clear with 2 m/s wind toward +x: 1 + 0.004 x 2 x dot(dir, +x).
            double tailwind = 2.0 * dirX;
            return clamp(1.0 + 0.004 * tailwind, 0.4, 1.1);
        }
        double windX = StrictMath.cos(windDirection) * windSpeed;
        double windZ = StrictMath.sin(windDirection) * windSpeed;
        double tailwind = dirX * windX + dirZ * windZ;
        double factor = 1.0
                - 0.08 * rain
                - 0.22 * heavyRain
                - 0.18 * snow
                - 0.10 * snowCover
                - 0.05 * puddles
                + 0.004 * tailwind;
        return clamp(factor, 0.4, 1.1);
    }

    @Override
    public double visibilityMetres() {
        return 25000.0 / (1.0 + 5.0 * rain + 24.0 * heavyRain + 10.0 * snow);
    }

    @Override
    public double combatFactor() {
        return clamp(visibilityMetres() / 5000.0, 0.3, 1.0);
    }

    /**
     * @return the human-readable weather conditions for this tick:
     *         {@code Sunny} when no precipitation is active, plus {@code Rain},
     *         {@code Heavy rain}, {@code Strong wind} and {@code Snow} as
     *         applicable
     */
    public List<String> conditions() {
        List<String> out = new java.util.ArrayList<>(4);
        if (rain <= 0.5 && heavyRain <= 0.5 && snow <= 0.5) {
            out.add("Sunny");
        }
        if (rain > 0.5) {
            out.add("Rain");
        }
        if (heavyRain > 0.5) {
            out.add("Heavy rain");
        }
        if (windSpeed >= STRONG_WIND_MIN) {
            out.add("Strong wind");
        }
        if (snow > 0.5) {
            out.add("Snow");
        }
        return List.copyOf(out);
    }


    /** Approaches {@code target} with the 40 s time constant; snaps on convergence. */
    private double approach(double value, double target) {
        double next = value + (target - value) * step;
        return Math.abs(next - target) < SNAP ? target : next;
    }

    /** Approaches an angular target along the shortest arc, then normalizes. */
    private double approachAngle(double value, double target) {
        double normalizedTarget = normalizeAngle(target);
        double diff = normalizedTarget - value;
        double next = value + diff * step;
        if (Math.abs(normalizeAngle(next - normalizedTarget)) < SNAP) {
            return normalizedTarget;
        }
        return normalizeAngle(next);
    }

    /** Normalizes an angle in radians to {@code (-pi, pi]}. */
    private static double normalizeAngle(double angle) {
        double a = angle % TWO_PI;
        if (a <= -StrictMath.PI) {
            a += TWO_PI;
        } else if (a > StrictMath.PI) {
            a -= TWO_PI;
        }
        return a;
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

}
