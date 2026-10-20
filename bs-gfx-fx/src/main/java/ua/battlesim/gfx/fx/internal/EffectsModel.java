package ua.battlesim.gfx.fx.internal;

import ua.battlesim.api.AttackEvent;
import ua.battlesim.api.RenderSnapshot;
import ua.battlesim.api.SimEvent;
import ua.battlesim.api.UnitDestroyedEvent;
import ua.battlesim.api.UnitView;
import ua.battlesim.api.WeatherState;

import java.util.List;
import java.util.Objects;

/**
 * The deterministic, CPU-side effect simulation of a round.
 *
 * <p>Time advances only via {@link #update(double, double, double, double)},
 * so pause and playback speed are respected. All randomness flows through a
 * seeded {@link FxRandom}; all trigonometry uses {@link StrictMath}; no hash
 * iteration order ever influences the result (lists are processed in order,
 * wrecks are iterated by ascending unit id).
 *
 * <p>Behaviour implemented here:
 * <ul>
 *   <li>{@code AttackEvent}: a muzzle flash, muzzle sparks and a small smoke
 *       puff at the attacker; a tracer travelling {@code distance / 600 m/s}
 *       clamped to {@code [0.06 s, 0.8 s]}; when the shell lands: an impact
 *       flash plus fire, sparks, dust and smoke (the "impact explosion");</li>
 *   <li>{@code UnitDestroyedEvent}: the wreck becomes visibly destroyed only
 *       after the last incoming killing shell lands, then continuously emits
 *       rising smoke and fire for the first 90 simulated seconds;</li>
 *   <li>rain streaks and snow flakes around the spawn centre (the camera
 *       target), at an intensity from the latest {@link WeatherState}.</li>
 * </ul>
 *
 * <p>Per {@code update} step the processing order is fixed: (1) particle
 * integration, (2) impact explosions for shells that landed, (3) continuous
 * wreck emissions in ascending unit id order, (4) weather spawning. Existing
 * particles are always aged before new effects spawn, so fresh effects are
 * never consumed by the same step that created them.
 *
 * <p>Pure CPU: no OpenGL, fully testable. Not thread-safe; driven from the
 * single render thread.
 */
public final class EffectsModel {

    /** The default deterministic seed. */
    public static final long DEFAULT_SEED = 0x5EED5EEDB15EAD5EL;

    /** Muzzle offset from the unit position in metres. */
    public static final double MUZZLE_OFFSET_METRES = 3.5;

    /** Muzzle height above the unit position in metres. */
    public static final double MUZZLE_HEIGHT_METRES = 1.2;

    /** Wreck smoke emission rate in particles per simulated second. */
    public static final double WRECK_SMOKE_RATE = 25.0;

    /** Wreck fire emission rate in particles per simulated second. */
    public static final double WRECK_FIRE_RATE = 50.0;

    /** How far below the spawn centre rain/snow are killed (metres). */
    public static final double PRECIPITATION_KILL_DEPTH = 15.0;

    private final FxRandom random;
    private final ParticlePool pool;
    private final TracerPool tracers = new TracerPool();
    private final WreckTracker wrecks = new WreckTracker();
    private final WeatherSpawner weatherSpawner = new WeatherSpawner();
    private WeatherState lastWeather;
    private double simTime;

    /** Creates a model with the default seed and pool capacity. */
    public EffectsModel() {
        this(DEFAULT_SEED, ParticlePool.DEFAULT_CAPACITY);
    }

    /**
     * @param seed      the deterministic seed
     * @param capacity  total particle pool size, {@code >= 512}
     */
    public EffectsModel(long seed, int capacity) {
        this.random = new FxRandom(seed);
        this.pool = new ParticlePool(capacity);
    }

    /** @return the current simulated time in seconds */
    public double simTime() {
        return simTime;
    }

    /** @return the latest delivered weather state, or null before the first event */
    public WeatherState weather() {
        return lastWeather;
    }

    /** @return the particle pool */
    public ParticlePool pool() {
        return pool;
    }

    /** @return the tracer pool */
    public TracerPool tracers() {
        return tracers;
    }

    /** @return the wreck tracker */
    public WreckTracker wrecks() {
        return wrecks;
    }

    /**
     * @param type the particle category
     * @return the number of live particles of that category
     */
    public int liveCount(ParticleType type) {
        return pool.liveCount(type);
    }

    /**
     * Consumes one batch of simulation events at the current simulated time.
     *
     * <p>Attack events are processed first (in list order) and then
     * destruction events (in list order), so a wreck is always registered
     * before or together with the impact that makes it visible.
     *
     * @param events    the drained events (non-null, elements non-null)
     * @param snapshot  the round state delivering the events (non-null)
     */
    public void onEvents(List<SimEvent> events, RenderSnapshot snapshot) {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(snapshot, "snapshot");
        for (SimEvent e : events) {
            Objects.requireNonNull(e, "event");
        }
        this.lastWeather = snapshot.weather();
        double t = simTime;
        for (SimEvent e : events) {
            if (e instanceof AttackEvent attack) {
                handleAttack(attack, snapshot, t);
            }
        }
        for (SimEvent e : events) {
            if (e instanceof UnitDestroyedEvent destroyed) {
                handleDestroyed(destroyed, snapshot, t);
            }
        }
    }

    private void handleAttack(AttackEvent event, RenderSnapshot snapshot, double t) {
        UnitView attacker = findUnit(snapshot, event.attackerId());
        UnitView target = findUnit(snapshot, event.targetId());
        if (attacker == null || target == null) {
            return; // defensive: the snapshot must reference both units
        }
        double heading = attacker.heading();
        double mx = attacker.x() + StrictMath.cos(heading) * MUZZLE_OFFSET_METRES;
        double mz = attacker.z() + StrictMath.sin(heading) * MUZZLE_OFFSET_METRES;

        // Muzzle flash
        pool.spawn(ParticleType.MUZZLE_FLASH, mx, MUZZLE_HEIGHT_METRES, mz,
                0.0, 0.0, 0.0,
                0.06 + random.nextDouble() * 0.05,
                1.1 + random.nextDouble() * 0.9, 1.0,
                1.0, 0.85, 0.5);

        // Muzzle sparks, in a cone around the heading
        for (int i = 0; i < 6; i++) {
            double angle = heading + (random.nextDouble() - 0.5) * 1.0;
            double speed = 8.0 + random.nextDouble() * 14.0;
            pool.spawn(ParticleType.SPARK, mx, MUZZLE_HEIGHT_METRES, mz,
                    StrictMath.cos(angle) * speed,
                    1.0 + random.nextDouble() * 2.5,
                    StrictMath.sin(angle) * speed,
                    0.2 + random.nextDouble() * 0.3,
                    0.22, 1.0,
                    1.0, 0.9, 0.55);
        }

        // Muzzle smoke puff
        for (int i = 0; i < 3; i++) {
            pool.spawn(ParticleType.SMOKE,
                    mx + (random.nextDouble() - 0.5) * 0.8,
                    MUZZLE_HEIGHT_METRES + (random.nextDouble() - 0.5) * 0.8,
                    mz + (random.nextDouble() - 0.5) * 0.8,
                    (random.nextDouble() - 0.5) * 1.5,
                    0.5 + random.nextDouble(),
                    (random.nextDouble() - 0.5) * 1.5,
                    0.7 + random.nextDouble() * 0.7,
                    0.5 + random.nextDouble() * 0.4, 0.5,
                    0.45, 0.44, 0.42);
        }

        // The in-flight shell
        double duration = TracerPool.travelSeconds(
                distance(attacker.x(), attacker.z(), target.x(), target.z()));
        tracers.add(attacker.x(), 0.8, attacker.z(),
                target.x(), 0.8, target.z(), t);
        if (event.killed()) {
            wrecks.noteKillingImpact(event.targetId(), t + duration);
        }
    }

    private void handleDestroyed(UnitDestroyedEvent event, RenderSnapshot snapshot, double t) {
        UnitView unit = findUnit(snapshot, event.victimId());
        if (unit == null) {
            return; // defensive: the snapshot must reference the victim
        }
        wrecks.noteDestroyed(event.victimId(), unit.x(), 0.5, unit.z(), t);
    }

    private static UnitView findUnit(RenderSnapshot snapshot, int id) {
        for (UnitView u : snapshot.units()) {
            if (u.id() == id) {
                return u;
            }
        }
        return null;
    }

    private static double distance(double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Advances the effect simulation by {@code simSeconds} simulated seconds.
     *
     * <p>Processing order (fixed, deterministic): (1) impact explosions for
     * shells that landed, (2) continuous wreck emissions in ascending unit
     * id order, (3) particle integration with the latest wind, (4) rain and
     * snow spawning around the spawn centre.
     *
     * @param simSeconds simulated seconds, finite and {@code >= 0}
     * @param spawnCx    rain/snow spawn centre x in metres (finite)
     * @param spawnCy    rain/snow spawn centre y in metres (finite)
     * @param spawnCz    rain/snow spawn centre z in metres (finite)
     */
    public void update(double simSeconds, double spawnCx, double spawnCy, double spawnCz) {
        for (double v : new double[] { simSeconds, spawnCx, spawnCy, spawnCz }) {
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("update inputs must be finite, got: " + v);
            }
        }
        if (simSeconds < 0.0) {
            throw new IllegalArgumentException("simSeconds must be >= 0: " + simSeconds);
        }
        double t = simTime + simSeconds;

        // 1) Age/integrate the existing particles with the latest wind first,
        //    so effects spawned below in this step live their full duration.
        double[] wind = windXZ(lastWeather);
        pool.update(simSeconds, wind[0], wind[1], spawnCy - PRECIPITATION_KILL_DEPTH);

        // 2) Impact explosions, in tracer insertion order.
        for (int i = 0; i < tracers.count(); i++) {
            if (t >= tracers.impactTime(i)) {
                spawnImpact(tracers.endX(i), tracers.endY(i), tracers.endZ(i));
            }
        }
        tracers.removeExpired(t);

        // 3) Continuous wreck emissions, ascending unit id.
        for (int i = 0; i < wrecks.count(); i++) {
            int id = wrecks.id(i);
            if (!wrecks.visible(id, t) || t >= wrecks.effectEnd(id)) {
                continue;
            }
            double wx = wrecks.x(i);
            double wy = wrecks.y(i);
            double wz = wrecks.z(i);

            wrecks.setSmokeAcc(i, wrecks.smokeAcc(i) + WRECK_SMOKE_RATE * simSeconds);
            int smoke = (int) wrecks.smokeAcc(i);
            wrecks.setSmokeAcc(i, wrecks.smokeAcc(i) - smoke);
            while (smoke-- > 0) {
                spawnWreckSmoke(wx, wy, wz);
            }

            wrecks.setFireAcc(i, wrecks.fireAcc(i) + WRECK_FIRE_RATE * simSeconds);
            int fire = (int) wrecks.fireAcc(i);
            wrecks.setFireAcc(i, wrecks.fireAcc(i) - fire);
            while (fire-- > 0) {
                spawnWreckFire(wx, wy, wz);
            }
        }
        wrecks.prune(t);

        // 4) Spawn fresh precipitation around the centre.
        if (lastWeather != null) {
            weatherSpawner.update(pool, lastWeather, spawnCx, spawnCy, spawnCz,
                    simSeconds, wind[0], wind[1], random);
        }

        simTime = t;
    }

    private void spawnImpact(double x, double y, double z) {
        // Bright impact flash
        pool.spawn(ParticleType.IMPACT_FLASH, x, y, z,
                0.0, 0.0, 0.0,
                0.1 + random.nextDouble() * 0.06,
                2.0 + random.nextDouble() * 1.6, 1.0,
                1.0, 0.8, 0.45);

        // Fire
        for (int i = 0; i < 10; i++) {
            pool.spawn(ParticleType.FIRE,
                    x + (random.nextDouble() - 0.5) * 1.0,
                    y + random.nextDouble() * 0.5,
                    z + (random.nextDouble() - 0.5) * 1.0,
                    (random.nextDouble() - 0.5) * 1.5,
                    1.0 + random.nextDouble() * 2.0,
                    (random.nextDouble() - 0.5) * 1.5,
                    0.5 + random.nextDouble() * 0.8,
                    0.8 + random.nextDouble() * 0.7, 1.0,
                    1.0, 0.4 + 0.25 * random.nextDouble(), 0.08);
        }

        // Radial sparks
        for (int i = 0; i < 20; i++) {
            double angle = (i / 20.0) * (2.0 * StrictMath.PI) + (random.nextDouble() - 0.5) * 0.4;
            double speed = 8.0 + random.nextDouble() * 18.0;
            pool.spawn(ParticleType.SPARK, x, y + 0.3, z,
                    StrictMath.cos(angle) * speed,
                    2.0 + random.nextDouble() * 6.0,
                    StrictMath.sin(angle) * speed,
                    0.3 + random.nextDouble() * 0.4,
                    0.25, 1.0,
                    1.0, 0.85, 0.5);
        }

        // Expanding dust
        for (int i = 0; i < 12; i++) {
            double angle = (i / 12.0) * (2.0 * StrictMath.PI) + (random.nextDouble() - 0.5) * 0.3;
            double speed = 3.0 + random.nextDouble() * 5.0;
            pool.spawn(ParticleType.DUST, x, y + 0.2, z,
                    StrictMath.cos(angle) * speed,
                    0.5 + random.nextDouble(),
                    StrictMath.sin(angle) * speed,
                    1.2 + random.nextDouble() * 1.3,
                    1.0 + random.nextDouble() * 1.2, 0.5,
                    0.52, 0.45, 0.35);
        }

        // Rising smoke
        for (int i = 0; i < 8; i++) {
            double gray = 0.32 + 0.18 * random.nextDouble();
            pool.spawn(ParticleType.SMOKE,
                    x + (random.nextDouble() - 0.5) * 1.4,
                    y + 0.5,
                    z + (random.nextDouble() - 0.5) * 1.4,
                    (random.nextDouble() - 0.5) * 1.2,
                    0.8 + random.nextDouble() * 0.7,
                    (random.nextDouble() - 0.5) * 1.2,
                    2.5 + random.nextDouble() * 2.0,
                    1.4 + random.nextDouble() * 1.6, 0.55,
                    gray, gray, gray);
        }
    }

    private void spawnWreckSmoke(double x, double y, double z) {
        double gray = 0.28 + 0.2 * random.nextDouble();
        pool.spawn(ParticleType.SMOKE,
                x + (random.nextDouble() - 0.5) * 1.8,
                y + 0.3 + random.nextDouble() * 0.8,
                z + (random.nextDouble() - 0.5) * 1.8,
                (random.nextDouble() - 0.5) * 1.2,
                0.8 + random.nextDouble() * 0.8,
                (random.nextDouble() - 0.5) * 1.2,
                3.0 + random.nextDouble() * 3.0,
                1.2 + random.nextDouble() * 1.8, 0.55,
                gray, gray, gray);
    }

    private void spawnWreckFire(double x, double y, double z) {
        pool.spawn(ParticleType.FIRE,
                x + (random.nextDouble() - 0.5) * 1.6,
                y + 0.2,
                z + (random.nextDouble() - 0.5) * 1.6,
                (random.nextDouble() - 0.5) * 0.8,
                1.0 + random.nextDouble() * 1.5,
                (random.nextDouble() - 0.5) * 0.8,
                0.4 + random.nextDouble() * 0.5,
                0.6 + random.nextDouble() * 0.8, 1.0,
                1.0, 0.45 + 0.2 * random.nextDouble(), 0.1);
    }

    private static double[] windXZ(WeatherState weather) {
        if (weather == null) {
            return new double[] { 0.0, 0.0 };
        }
        double speed = weather.windSpeed();
        double direction = weather.windDirection();
        return new double[] { speed * StrictMath.cos(direction), speed * StrictMath.sin(direction) };
    }

    /**
     * A deterministic checksum over the whole model state (particles,
     * tracers, wrecks, time), for determinism tests.
     */
    public long fingerprint() {
        long h = pool.fingerprint();
        h ^= Long.rotateLeft(tracers.count() * 0x9E3779B97F4A7C15L, 7);
        h ^= Long.rotateLeft(wrecks.fingerprint(), 13);
        h ^= Long.rotateLeft(Double.doubleToLongBits(simTime), 5);
        return h;
    }
}