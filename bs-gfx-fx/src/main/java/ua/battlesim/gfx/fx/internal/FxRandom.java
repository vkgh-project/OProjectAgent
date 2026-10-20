package ua.battlesim.gfx.fx.internal;

/**
 * A small deterministic pseudo-random number generator (splitmix64) used by
 * all effect spawning.
 *
 * <p>Two generators created with the same seed produce the identical value
 * sequence on every platform and JVM version, which keeps the simulation
 * rendering deterministic (no wall clock, no unseeded randomness).
 */
public final class FxRandom {

    private long state;

    /**
     * @param seed the deterministic seed (any value)
     */
    public FxRandom(long seed) {
        this.state = seed;
    }

    /** @return the next pseudo-random {@code long} */
    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** @return a uniformly distributed value in {@code [0, 1)} */
    public double nextDouble() {
        return (nextLong() >>> 11) * (1.0 / 9007199254740992.0);
    }

    /**
     * @param min lower bound (inclusive), finite
     * @param max upper bound (exclusive), finite and {@code >= min}
     * @return a value in {@code [min, max)}
     */
    public double nextDouble(double min, double max) {
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            throw new IllegalArgumentException("bounds must be finite: " + min + ", " + max);
        }
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max: " + min + " > " + max);
        }
        return min + (max - min) * nextDouble();
    }

    /**
     * @param bound the exclusive upper bound, {@code >= 1}
     * @return a value in {@code [0, bound)}
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be >= 1: " + bound);
        }
        return (int) Long.remainderUnsigned(nextLong(), (long) bound);
    }

    /**
     * @param min lower bound (inclusive)
     * @param max upper bound (exclusive), {@code >= min}
     * @return a value in {@code [min, max)}
     */
    public int nextInt(int min, int max) {
        if (max < min) {
            throw new IllegalArgumentException("max must be >= min: " + max + " < " + min);
        }
        return min + nextInt(max - min);
    }

    /** @return the next pseudo-random boolean */
    public boolean nextBoolean() {
        return (nextLong() & 1L) == 0L;
    }
}
