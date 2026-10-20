package ua.battlesim.gfx.fx.internal;

/**
 * A fixed-capacity, CPU-side particle pool (default 24000 slots) that owns
 * all effect particles of a round.
 *
 * <p>The pool is partitioned into contiguous per-type blocks
 * ({@link ParticleType#poolFraction()} of the capacity, minimum 8 slots), so
 * a heavy rain storm can never evict smoke. Within a block new particles
 * overwrite the oldest slot of that type (ring buffer), which keeps the
 * particle count bounded and the iteration order purely slot-index order —
 * there is no hash-order dependence anywhere.
 *
 * <p>Integration is explicit Euler with a per-type physical model
 * (sparks: gravity; smoke: buoyancy and growth; dust: settling and growth;
 * rain/snow: fall speed plus wind drift; snow: sinusoidal drift). All
 * trigonometry uses {@link StrictMath}.
 *
 * <p>Pure CPU: no OpenGL, fully testable. Not thread-safe; driven from the
 * single render thread.
 */
public final class ParticlePool {

    /** The default total pool size (about 24000 slots). */
    public static final int DEFAULT_CAPACITY = 24000;

    /** The smallest supported pool size. */
    public static final int MIN_CAPACITY = 512;

    private static final double GRAVITY = 9.8;
    private static final int MIN_BLOCK = 8;

    private final int capacity;
    private final int[] blockBase = new int[ParticleType.values().length];
    private final int[] blockSize = new int[ParticleType.values().length];
    private final int[] ring = new int[ParticleType.values().length];
    private final int[] live = new int[ParticleType.values().length];
    private final int[] typeOf;

    private final float[] px;
    private final float[] py;
    private final float[] pz;
    private final float[] vx;
    private final float[] vy;
    private final float[] vz;
    private final float[] age;
    private final float[] life;
    private final float[] size;
    private final float[] baseSize;
    private final float[] alpha;
    private final float[] baseAlpha;
    private final float[] r;
    private final float[] g;
    private final float[] b;
    private final boolean[] alive;

    /**
     * @param capacity total pool size in {@code [512, ...]}
     */
    public ParticlePool(int capacity) {
        if (capacity < MIN_CAPACITY) {
            throw new IllegalArgumentException("capacity must be >= " + MIN_CAPACITY + ": " + capacity);
        }
        this.capacity = capacity;
        int sum = 0;
        for (ParticleType type : ParticleType.values()) {
            int block = Math.max(MIN_BLOCK, (int) Math.round(capacity * type.poolFraction()));
            blockSize[type.ordinal()] = block;
            sum += block;
        }
        // Fold the rounding difference into the largest block (smoke).
        int smoke = ParticleType.SMOKE.ordinal();
        blockSize[smoke] += capacity - sum;
        if (blockSize[smoke] < MIN_BLOCK) {
            throw new IllegalArgumentException("capacity too small for the smoke block: " + capacity);
        }
        int cursor = 0;
        for (int t = 0; t < ParticleType.values().length; t++) {
            blockBase[t] = cursor;
            cursor += blockSize[t];
        }

        this.typeOf = new int[capacity];
        for (int t = 0; t < ParticleType.values().length; t++) {
            int base = blockBase[t];
            int size = blockSize[t];
            for (int i = 0; i < size; i++) {
                typeOf[base + i] = t;
            }
        }

        this.px = new float[capacity];
        this.py = new float[capacity];
        this.pz = new float[capacity];
        this.vx = new float[capacity];
        this.vy = new float[capacity];
        this.vz = new float[capacity];
        this.age = new float[capacity];
        this.life = new float[capacity];
        this.size = new float[capacity];
        this.baseSize = new float[capacity];
        this.alpha = new float[capacity];
        this.baseAlpha = new float[capacity];
        this.r = new float[capacity];
        this.g = new float[capacity];
        this.b = new float[capacity];
        this.alive = new boolean[capacity];
    }

    /** @return the total pool capacity in slots */
    public int capacity() {
        return capacity;
    }

    /**
     * @param type the particle category
     * @return the number of slots reserved for that category
     */
    public int blockSize(ParticleType type) {
        return blockSize[type.ordinal()];
    }

    /**
     * @param type the particle category
     * @return the number of currently alive particles of that category
     */
    public int liveCount(ParticleType type) {
        return live[type.ordinal()];
    }

    /**
     * Advances every live particle by {@code dt} simulated seconds, applying
     * the per-type model and wind drift, and kills expired particles or
     * rain/snow that crossed the kill plane.
     *
     * @param dt         simulated seconds, finite and {@code >= 0}
     * @param windX      wind velocity x component in m/s
     * @param windZ      wind velocity z component in m/s
     * @param killPlaneY y coordinate below which RAIN/SNOW particles are killed
     */
    public void update(double dt, double windX, double windZ, double killPlaneY) {
        for (double v : new double[] { dt, windX, windZ, killPlaneY }) {
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("update inputs must be finite, got: " + v);
            }
        }
        if (dt < 0.0) {
            throw new IllegalArgumentException("dt must be >= 0: " + dt);
        }

        for (int i = 0; i < capacity; i++) {
            if (!alive[i]) {
                continue;
            }
            age[i] += (float) dt;
            if (age[i] >= life[i]) {
                kill(i);
                continue;
            }
            int t = typeOf[i];
            double frac = Math.min(1.0, age[i] / life[i]);
            double inv = 1.0 - frac;
            switch (t) {
                case 0 -> { // MUZZLE_FLASH
                    size[i] = (float) (baseSize[i] * (1.0 + 2.0 * frac));
                    alpha[i] = (float) (baseAlpha[i] * inv * inv);
                }
                case 1 -> { // IMPACT_FLASH
                    size[i] = (float) (baseSize[i] * (1.0 + 2.5 * frac));
                    alpha[i] = (float) (baseAlpha[i] * inv * inv);
                }
                case 2 -> { // FIRE
                    vy[i] += 1.5f * (float) dt;
                    size[i] = (float) (baseSize[i] * (1.0 + 0.6 * frac));
                    alpha[i] = (float) (baseAlpha[i] * inv * inv);
                }
                case 3 -> { // SPARK
                    vy[i] -= GRAVITY * (float) dt;
                    alpha[i] = (float) (baseAlpha[i] * inv);
                }
                case 4 -> { // DUST
                    vy[i] -= 0.5f * (float) dt;
                    size[i] = (float) (baseSize[i] * (1.0 + 1.5 * frac));
                    alpha[i] = (float) (baseAlpha[i] * inv);
                }
                case 5 -> { // SMOKE
                    vy[i] += 0.6f * (float) dt;
                    size[i] = (float) (baseSize[i] * (1.0 + 2.0 * frac));
                    alpha[i] = (float) (baseAlpha[i] * (0.2 + 0.8 * inv));
                }
                case 6 -> { // RAIN
                    vy[i] -= 3.0f * (float) dt;
                }
                case 7 -> { // SNOW
                    px[i] += (float) (StrictMath.sin(age[i] * 2.0 + i * 0.61803) * 1.0 * dt);
                    vy[i] -= 0.3f * (float) dt;
                }
                default -> throw new IllegalStateException("unknown particle type ordinal: " + t);
            }

            double wf = windFactor(t);
            px[i] += (float) ((vx[i] + windX * wf) * dt);
            py[i] += (float) (vy[i] * dt);
            pz[i] += (float) ((vz[i] + windZ * wf) * dt);

            if ((t == 6 || t == 7) && py[i] < killPlaneY) {
                kill(i);
            }
        }
    }

    /**
     * A deterministic checksum over all live particle state, for
     * determinism tests (identical call sequences must produce identical
     * fingerprints).
     */
    public long fingerprint() {
        long h = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < capacity; i++) {
            if (alive[i]) {
                h = mix(h, Float.floatToIntBits(px[i]));
                h = mix(h, Float.floatToIntBits(py[i]));
                h = mix(h, Float.floatToIntBits(pz[i]));
                h = mix(h, Float.floatToIntBits(age[i]));
                h = mix(h, Float.floatToIntBits(size[i]));
                h = mix(h, Float.floatToIntBits(alpha[i]));
            }
        }
        for (int t = 0; t < live.length; t++) {
            h = mix(h, live[t]);
            h = mix(h, ring[t]);
        }
        return h;
    }

    private void kill(int slot) {
        if (alive[slot]) {
            alive[slot] = false;
            live[typeOf[slot]]--;
        }
    }

    private static double windFactor(int type) {
        return switch (type) {
            case 0, 1 -> 0.05;   // flashes barely drift
            case 2 -> 0.35;      // fire
            case 3 -> 0.10;      // sparks
            case 4 -> 0.50;      // dust
            case 5 -> 0.30;      // smoke
            case 6 -> 0.90;      // rain follows the wind
            case 7 -> 0.35;      // snow
            default -> 0.0;
        };
    }

    private static long mix(long h, long v) {
        h ^= v + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
        return h;
    }

    /**
     * Spawns one particle, overwriting the oldest slot of the type block when
     * the block is full.
     *
     * @return the global slot index ({@code [0, capacity)}), for tests
     */
    public int spawn(ParticleType type, double x, double y, double z,
            double vx, double vy, double vz, double lifeSeconds, double sizeMetres,
            double alpha, double r, double g, double b) {
        for (double v : new double[] { x, y, z, vx, vy, vz, sizeMetres, r, g, b }) {
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("particle fields must be finite, got: " + v);
            }
        }
        if (lifeSeconds <= 0.0 || !Double.isFinite(lifeSeconds)) {
            throw new IllegalArgumentException("life must be finite and > 0: " + lifeSeconds);
        }
        if (sizeMetres <= 0.0) {
            throw new IllegalArgumentException("size must be > 0: " + sizeMetres);
        }
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("alpha must be in [0,1]: " + alpha);
        }

        int t = type.ordinal();
        int local = ring[t];
        int slot = blockBase[t] + local;
        ring[t] = (local + 1) % blockSize[t];
        if (!alive[slot]) {
            live[t]++;
        }

        this.px[slot] = (float) x;
        this.py[slot] = (float) y;
        this.pz[slot] = (float) z;
        this.vx[slot] = (float) vx;
        this.vy[slot] = (float) vy;
        this.vz[slot] = (float) vz;
        this.age[slot] = 0.0f;
        this.life[slot] = (float) lifeSeconds;
        this.size[slot] = (float) sizeMetres;
        this.baseSize[slot] = (float) sizeMetres;
        this.alpha[slot] = (float) alpha;
        this.baseAlpha[slot] = (float) alpha;
        this.r[slot] = (float) r;
        this.g[slot] = (float) g;
        this.b[slot] = (float) b;
        this.alive[slot] = true;
        return slot;
    }

    /**
     * @param type  the particle category
     * @param local the slot index within the type block
     * @return true if that slot holds a live particle
     */
    public boolean isAlive(ParticleType type, int local) {
        int block = blockSize[type.ordinal()];
        if (local < 0 || local >= block) {
            throw new IndexOutOfBoundsException("local slot out of range: " + local);
        }
        return alive[blockBase[type.ordinal()] + local];
    }

    /**
     * @param type  the particle category
     * @param local the slot index within the type block
     * @return an immutable snapshot of that slot (valid for dead slots too)
     */
    public ParticleState state(ParticleType type, int local) {
        if (local < 0 || local >= blockSize[type.ordinal()]) {
            throw new IndexOutOfBoundsException("local slot out of range: " + local);
        }
        int slot = blockBase[type.ordinal()] + local;
        return new ParticleState(px[slot], py[slot], pz[slot], vx[slot], vy[slot], vz[slot],
                age[slot], life[slot], size[slot], alpha[slot], r[slot], g[slot], b[slot]);
    }
}

