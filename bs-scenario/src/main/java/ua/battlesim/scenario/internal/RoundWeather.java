package ua.battlesim.scenario.internal;

import ua.battlesim.api.WeatherModel;
import ua.battlesim.api.WeatherState;
import ua.battlesim.math.DetRandom;

/**
 * The deterministic per-round weather model of the standard scenario.
 *
 * <p>The state of a tick is a pure function of {@code (seed, tick)}: each
 * tick draws five values from a fresh {@link DetRandom} seeded with
 * {@code seed + tick x 0x9E3779B97F4A7C15}, so stepping is repeatable and no
 * incremental state can drift. Only {@link DetRandom} and {@link StrictMath}
 * are consulted — no wall clock, no unseeded randomness.
 *
 * <p>Channels of an enabled tick (precipitation in {@code [0, 1]}):
 * {@code rain} is 0 while {@code rollRain < 0.70}, heavy rain only during
 * rain, {@code snow} starts while {@code rollSnow >= 0.80}; wind speed is in
 * {@code [1, 9]} m/s with direction in {@code [-pi, pi]}; cloud, wetness,
 * puddles and snow cover are derived from the precipitation.
 *
 * <p>Factors (dimensionless, in {@code [0, 1]}):
 * {@code speedFactor(dir)} = clamp(1 - 0.05 rain - 0.10 heavy - 0.08 snow
 * - 0.03 snowCover - 0.02 puddles - 0.002 dot(dir, wind), 0.25, 1) with the
 * dot product of the unit movement direction (x east, z south) and the wind
 * vector (tailwind positive) and a zero direction giving 1;
 * {@code visibility} = clamp(1 - 0.35 rain - 0.30 heavy - 0.35 snow, 0.1, 1);
 * {@code combatFactor} = clamp(1 - 0.20 rain - 0.20 heavy - 0.15 snow, 0.3, 1).
 *
 * <p>With {@code enabled = false} the model is permanently clear: every
 * channel is 0 and all three factors are 1.
 *
 * <p>Threading: used from the single simulation worker thread; stepping a
 * tick that was already stepped is a no-op, only strictly forward ticks
 * advance the model.
 */
public final class RoundWeather implements WeatherModel {

    /** SplitMix64 step constant (the golden ratio), as in {@link DetRandom}. */
    private static final long GOLDEN_RATIO = 0x9E3779B97F4A7C15L;

    /** The permanently clear state (all channels 0). */
    private static final WeatherState CLEAR =
            new WeatherState(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    private final long seed;
    private final boolean enabled;
    private int tick = -1;
    private WeatherState state;

    /**
     * @param seed    the weather seed (derived from the round seed)
     * @param enabled whether precipitation and wind variation are active
     */
    public RoundWeather(long seed, boolean enabled) {
        this.seed = seed;
        this.enabled = enabled;
        this.state = stateFor(-1);
    }

    /** @return the seed this weather model was constructed with */
    public long seed() {
        return seed;
    }

    /** @return whether precipitation and wind variation are active */
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void step(int tick) {
        if (tick <= this.tick) {
            return; // only strictly forward ticks advance the model
        }
        this.tick = tick;
        this.state = stateFor(tick);
    }

    @Override
    public WeatherState state() {
        return state;
    }

    @Override
    public double speedFactor(double dirX, double dirZ) {
        if (!Double.isFinite(dirX) || !Double.isFinite(dirZ)) {
            throw new IllegalArgumentException(
                    "direction must be finite: (" + dirX + ", " + dirZ + ")");
        }
        if (!enabled) {
            return 1.0;
        }
        WeatherState s = state;
        double len = StrictMath.hypot(dirX, dirZ);
        if (len == 0.0) {
            return 1.0;
        }
        double dx = dirX / len;
        double dz = dirZ / len;
        double windX = StrictMath.cos(s.windDirection()) * s.windSpeed();
        double windZ = StrictMath.sin(s.windDirection()) * s.windSpeed();
        double dot = dx * windX + dz * windZ;
        double f = 1.0 - 0.05 * s.rain() - 0.10 * s.heavyRain() - 0.08 * s.snow()
                - 0.03 * s.snowCover() - 0.02 * s.puddles() - 0.002 * dot;
        return clamp(f, 0.25, 1.0);
    }

    @Override
    public double visibility() {
        if (!enabled) {
            return 1.0;
        }
        WeatherState s = state;
        return clamp(1.0 - 0.35 * s.rain() - 0.30 * s.heavyRain() - 0.35 * s.snow(), 0.1, 1.0);
    }

    @Override
    public double combatFactor() {
        if (!enabled) {
            return 1.0;
        }
        WeatherState s = state;
        return clamp(1.0 - 0.20 * s.rain() - 0.20 * s.heavyRain() - 0.15 * s.snow(), 0.3, 1.0);
    }

    /** The immutable state of one tick; a pure function of the seed and tick. */
    private WeatherState stateFor(int t) {
        if (!enabled) {
            return CLEAR;
        }
        DetRandom r = new DetRandom(seed + (long) t * GOLDEN_RATIO);
        double rollRain = r.nextDouble();
        double rollHeavy = r.nextDouble();
        double rollSnow = r.nextDouble();
        double rollWindSpeed = r.nextDouble();
        double rollWindDir = r.nextDouble();

        double rain = rollRain < 0.70 ? 0.0 : (rollRain - 0.70) / 0.30;
        double heavy = (rollRain >= 0.70 && rollHeavy < 0.35) ? rollHeavy / 0.35 : 0.0;
        double snow = rollSnow < 0.80 ? 0.0 : (rollSnow - 0.80) / 0.20;
        double windSpeed = 1.0 + 8.0 * rollWindSpeed;
        double windDirection = StrictMath.PI * (2.0 * rollWindDir - 1.0);
        double cloud = clamp(0.2 + 0.8 * StrictMath.max(rain, heavy), 0.0, 1.0);
        double wetness = clamp(0.9 * StrictMath.max(rain, heavy), 0.0, 1.0);
        double puddles = clamp(0.6 * (rain + 0.5 * heavy), 0.0, 1.0);
        double snowCover = clamp(0.5 * snow, 0.0, 1.0);
        return new WeatherState(rain, heavy, snow, windSpeed, windDirection,
                cloud, wetness, puddles, snowCover);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : v > max ? max : v;
    }
}