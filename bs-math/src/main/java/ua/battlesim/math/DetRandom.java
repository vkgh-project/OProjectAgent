package ua.battlesim.math;

import java.util.Objects;

/**
 * Deterministic pseudo-random number generator based on SplitMix64.
 *
 * <p>The generator is a pure function of its seed: two instances constructed
 * with the same seed produce byte-identical sequences. No wall clock and no
 * unseeded randomness are ever consulted, and the state is instance-local
 * (no static mutable state).
 *
 * <p>{@link #fork(long)} derives an independent child stream from the current
 * parent state and a salt; it does not consume values from the parent
 * sequence, so the parent continues exactly where it left off. Forking the
 * same parent state with the same salt always yields the same child stream,
 * which keeps golden-seed reproduction possible.
 *
 * <p>Thread-safety: the instance is mutable and must be used from the single
 * simulation worker thread; it is not safe for concurrent use.
 */
public final class DetRandom {

    /** SplitMix64 step constant (the golden ratio). */
    private static final long GAMMA = 0x9E3779B97F4A7C15L;

    private long state;

    /**
     * @param seed the seed; any 64-bit value, the same seed gives the same
     *             sequence
     */
    public DetRandom(long seed) {
        this.state = seed;
    }

    /**
     * @return the next pseudorandom 64-bit value (full 64-bit range)
     */
    public long nextLong() {
        long z = (state += GAMMA);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * @return the next pseudorandom value in {@code [0.0, 1.0)}
     */
    public double nextDouble() {
        // top 53 bits -> uniformly distributed double in [0, 1)
        return (nextLong() >>> 11) * 1.1102230246251565E-16; // 2^-53
    }

    /**
     * @return the next pseudorandom value in {@code [0.0f, 1.0f)}
     */
    public float nextFloat() {
        // top 24 bits -> uniformly distributed float in [0, 1)
        return (float) ((nextLong() >>> 40) * 5.9604644775390625E-8); // 2^-24
    }

    /**
     * @param bound the exclusive upper bound, must be {@code > 0}
     * @return the next pseudorandom value in {@code [0, bound)} without
     *         modulo bias (rejection sampling, deterministic)
     * @throws IllegalArgumentException if {@code bound <= 0}
     */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be > 0: " + bound);
        }
        if (bound == 1) {
            return 0;
        }
        long limit = Long.MAX_VALUE / bound * bound;
        long x;
        do {
            x = nextLong() & Long.MAX_VALUE; // non-negative 63-bit value
        } while (x >= limit);
        return (int) (x % bound);
    }

    /**
     * @return the next pseudorandom boolean
     */
    public boolean nextBoolean() {
        return (nextLong() & 1L) == 1L;
    }

    /**
     * Derives an independent child stream from the current parent state and
     * the given salt. The parent sequence is not advanced; fork(42) on two
     * equally-seeded parents gives byte-identical child streams.
     *
     * @param salt mixes into the derived seed; different salts (almost
     *             surely) give different streams
     * @return a new independent generator
     */
    public DetRandom fork(long salt) {
        Objects.requireNonNull(this, "this");
        long z = state ^ (salt * -7046029254386353131L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return new DetRandom(z ^ (z >>> 31));
    }
}
