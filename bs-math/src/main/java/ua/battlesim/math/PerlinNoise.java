package ua.battlesim.math;

import static java.lang.Double.isFinite;

/**
 * Deterministic 2-D Perlin gradient noise (classic formulation with a
 * seeded permutation table and quintic fade).
 *
 * <p>Coordinates are in arbitrary map units (metres in this project). The
 * same seed and inputs always produce the same values: the permutation table
 * is built with {@link DetRandom}, so no unseeded randomness is involved.
 *
 * <p>Value ranges: {@link #get} returns values in roughly {@code [-1, 1]}
 * (the exact bound for 2-D Perlin is {@code sqrt(2) / 2} ~= 0.707);
 * {@link #fbm} normalised to {@code [-1, 1]}; {@link #ridged} normalised to
 * {@code [0, 1]}.
 *
 * <p>Thread-safe: the instance is immutable after construction.
 */
public final class PerlinNoise {

    private static final int PERM_SIZE = 256;

    /** Padded permutation table, {@code perm[x + perm[y]]} without bounds checks. */
    private final int[] perm;

    /**
     * @param seed the seed; the same seed builds the same permutation table
     */
    public PerlinNoise(long seed) {
        DetRandom r = new DetRandom(seed);
        int[] p = new int[PERM_SIZE];
        for (int i = 0; i < PERM_SIZE; i++) {
            p[i] = i;
        }
        // deterministic Fisher-Yates shuffle
        for (int i = PERM_SIZE - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }
        perm = new int[PERM_SIZE * 2];
        for (int i = 0; i < PERM_SIZE * 2; i++) {
            perm[i] = p[i & (PERM_SIZE - 1)];
        }
    }

    /**
     * Samples the noise field.
     *
     * @param x sample x coordinate (map units), finite
     * @param y sample y coordinate (map units), finite
     * @return a value in roughly {@code [-1, 1]}
     * @throws IllegalArgumentException if {@code x} or {@code y} is not finite
     */
    public double get(double x, double y) {
        if (!isFinite(x)) {
            throw new IllegalArgumentException("x must be finite: " + x);
        }
        if (!isFinite(y)) {
            throw new IllegalArgumentException("y must be finite: " + y);
        }
        double fx = StrictMath.floor(x);
        double fy = StrictMath.floor(y);
        double xf = x - fx;
        double yf = y - fy;
        int xi = ((int) fx) & (PERM_SIZE - 1);
        int yi = ((int) fy) & (PERM_SIZE - 1);

        double u = fade(xf);
        double v = fade(yf);

        double n00 = grad(perm[xi + perm[yi]], xf, yf);
        double n10 = grad(perm[xi + 1 + perm[yi]], xf - 1.0, yf);
        double n01 = grad(perm[xi + perm[yi + 1]], xf, yf - 1.0);
        double n11 = grad(perm[xi + 1 + perm[yi + 1]], xf - 1.0, yf - 1.0);

        double nx0 = n00 + (n10 - n00) * u;
        double nx1 = n01 + (n11 - n01) * u;
        return nx0 + (nx1 - nx0) * v;
    }

    /**
     * Fractal Brownian motion: a sum of {@code octaves} noise layers whose
     * frequency multiplies by {@code lacunarity} and amplitude by {@code
     * gain} per octave, normalised by the total amplitude.
     *
     * @param x          sample x coordinate (map units), finite
     * @param y          sample y coordinate (map units), finite
     * @param octaves    number of layers, {@code >= 1}
     * @param lacunarity frequency multiplier per octave, finite, {@code >= 1}
     * @param gain       amplitude multiplier per octave, finite, in {@code (0, 1)}
     * @return a value in {@code [-1, 1]}
     * @throws IllegalArgumentException on an invalid argument
     */
    public double fbm(double x, double y, int octaves, double lacunarity, double gain) {
        checkOctaves(octaves);
        checkLacunarity(lacunarity);
        checkGain(gain);
        if (!isFinite(x)) {
            throw new IllegalArgumentException("x must be finite: " + x);
        }
        if (!isFinite(y)) {
            throw new IllegalArgumentException("y must be finite: " + y);
        }
        double amp = 1.0;
        double freq = 1.0;
        double sum = 0.0;
        double norm = 0.0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * get(x * freq, y * freq);
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    /**
     * Ridged multifractal: per octave the value {@code 1 - |noise|} is
     * squared, weighted and normalised, giving sharp ridges.
     *
     * @param x          sample x coordinate (map units), finite
     * @param y          sample y coordinate (map units), finite
     * @param octaves    number of layers, {@code >= 1}
     * @param lacunarity frequency multiplier per octave, finite, {@code >= 1}
     * @param gain       amplitude multiplier per octave, finite, in {@code (0, 1)}
     * @return a value in {@code [0, 1]}
     * @throws IllegalArgumentException on an invalid argument
     */
    public double ridged(double x, double y, int octaves, double lacunarity, double gain) {
        checkOctaves(octaves);
        checkLacunarity(lacunarity);
        checkGain(gain);
        if (!isFinite(x)) {
            throw new IllegalArgumentException("x must be finite: " + x);
        }
        if (!isFinite(y)) {
            throw new IllegalArgumentException("y must be finite: " + y);
        }
        double amp = 1.0;
        double freq = 1.0;
        double sum = 0.0;
        double norm = 0.0;
        for (int i = 0; i < octaves; i++) {
            double n = 1.0 - StrictMath.abs(get(x * freq, y * freq));
            sum += amp * n * n;
            norm += amp;
            amp *= gain;
            freq *= lacunarity;
        }
        return sum / norm;
    }

    private static void checkOctaves(int octaves) {
        if (octaves < 1) {
            throw new IllegalArgumentException("octaves must be >= 1: " + octaves);
        }
    }

    private static void checkLacunarity(double lacunarity) {
        if (!isFinite(lacunarity) || lacunarity < 1.0) {
            throw new IllegalArgumentException("lacunarity must be finite and >= 1: " + lacunarity);
        }
    }

    private static void checkGain(double gain) {
        if (!isFinite(gain) || gain <= 0.0 || gain >= 1.0) {
            throw new IllegalArgumentException("gain must be finite and in (0, 1): " + gain);
        }
    }

    /** Quintic fade {@code 6t^5 - 15t^4 + 10t^3} (C2-continuous). */
    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    /** Gradient of one of 8 directions at offset (x, y). */
    private static double grad(int hash, double x, double y) {
        switch (hash & 7) {
            case 0:
                return x + y;
            case 1:
                return x - y;
            case 2:
                return -x + y;
            case 3:
                return -x - y;
            case 4:
                return x;
            case 5:
                return -x;
            case 6:
                return y;
            default:
                return -y;
        }
    }
}


