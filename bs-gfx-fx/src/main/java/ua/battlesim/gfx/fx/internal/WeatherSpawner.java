package ua.battlesim.gfx.fx.internal;

import ua.battlesim.api.WeatherState;

/**
 * Spawns rain streaks and snow flakes in a box around the camera target, at
 * a rate proportional to the {@link WeatherState} intensity.
 *
 * <p>Spawning is a pure function of the simulated time step, the weather
 * state, the spawn centre and the deterministic {@link FxRandom} sequence —
 * pause and playback speed are respected because only simulated seconds are
 * passed in. No OpenGL, fully testable.
 */
public final class WeatherSpawner {

    /** Horizontal half-extent of the rain spawn box in metres. */
    public static final double RAIN_BOX_METRES = 60.0;

    /** Horizontal half-extent of the snow spawn box in metres. */
    public static final double SNOW_BOX_METRES = 75.0;

    private double rainAccumulator;
    private double snowAccumulator;

    /**
     * Spawns new rain and snow particles for this simulated step.
     *
     * @param pool    the particle pool to spawn into (non-null)
     * @param weather the current weather state (non-null)
     * @param cx      spawn centre x in metres (finite)
     * @param cy      spawn centre y in metres (finite)
     * @param cz      spawn centre z in metres (finite)
     * @param dt      simulated seconds, {@code >= 0}
     * @param windX   wind velocity x in m/s
     * @param windZ   wind velocity z in m/s
     * @param random  the deterministic random source (non-null)
     */
    public void update(ParticlePool pool, WeatherState weather, double cx, double cy, double cz,
            double dt, double windX, double windZ, FxRandom random) {
        if (pool == null) {
            throw new NullPointerException("pool");
        }
        if (weather == null) {
            throw new NullPointerException("weather");
        }
        if (random == null) {
            throw new NullPointerException("random");
        }
        for (double v : new double[] { cx, cy, cz, dt, windX, windZ }) {
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("spawner inputs must be finite, got: " + v);
            }
        }
        if (dt < 0.0) {
            throw new IllegalArgumentException("dt must be >= 0: " + dt);
        }
        if (dt > 0.0) {
            spawnRain(pool, weather, cx, cy, cz, dt, windX, windZ, random);
            spawnSnow(pool, weather, cx, cy, cz, dt, random);
        }
    }

    private void spawnRain(ParticlePool pool, WeatherState weather, double cx, double cy, double cz,
            double dt, double windX, double windZ, FxRandom random) {
        int block = pool.blockSize(ParticleType.RAIN);
        double intensity = clamp01(weather.rain() + weather.heavyRain());
        int target = (int) Math.round(intensity * block);
        int deficit = target - pool.liveCount(ParticleType.RAIN);
        if (deficit <= 0) {
            rainAccumulator = 0.0;
            return;
        }
        // Steady state fills the target in about three simulated seconds.
        rainAccumulator += (block / 3.0) * dt;
        int n = (int) rainAccumulator;
        rainAccumulator -= n;
        n = Math.min(n, deficit);
        double heavy = clamp01(weather.heavyRain());
        while (n-- > 0) {
            double x = cx + (random.nextDouble() - 0.5) * 2.0 * RAIN_BOX_METRES;
            double z = cz + (random.nextDouble() - 0.5) * 2.0 * RAIN_BOX_METRES;
            double y = cy + 30.0 + random.nextDouble() * 40.0;
            double fall = -(12.0 + 12.0 * heavy + random.nextDouble() * 6.0);
            pool.spawn(ParticleType.RAIN, x, y, z,
                    windX * 0.9 + (random.nextDouble() - 0.5) * 2.0,
                    fall,
                    windZ * 0.9 + (random.nextDouble() - 0.5) * 2.0,
                    5.0,
                    0.15, 0.4,
                    0.55, 0.62, 0.78);
        }
    }

    private void spawnSnow(ParticlePool pool, WeatherState weather, double cx, double cy, double cz,
            double dt, FxRandom random) {
        int block = pool.blockSize(ParticleType.SNOW);
        double intensity = clamp01(weather.snow());
        int target = (int) Math.round(intensity * block);
        int deficit = target - pool.liveCount(ParticleType.SNOW);
        if (deficit <= 0) {
            snowAccumulator = 0.0;
            return;
        }
        snowAccumulator += (block / 5.0) * dt;
        int n = (int) snowAccumulator;
        snowAccumulator -= n;
        n = Math.min(n, deficit);
        double snow = clamp01(weather.snow());
        while (n-- > 0) {
            double x = cx + (random.nextDouble() - 0.5) * 2.0 * SNOW_BOX_METRES;
            double z = cz + (random.nextDouble() - 0.5) * 2.0 * SNOW_BOX_METRES;
            double y = cy + 15.0 + random.nextDouble() * 45.0;
            double fall = -(1.2 + 1.8 * snow);
            pool.spawn(ParticleType.SNOW, x, y, z,
                    (random.nextDouble() - 0.5) * 1.0,
                    fall,
                    (random.nextDouble() - 0.5) * 1.0,
                    10.0,
                    0.22 + random.nextDouble() * 0.12, 0.85,
                    0.98, 0.98, 1.0);
        }
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}