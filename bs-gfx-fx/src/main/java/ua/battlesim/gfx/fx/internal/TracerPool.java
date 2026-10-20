package ua.battlesim.gfx.fx.internal;

/**
 * The in-flight tracer shells of a round: bright tracers travelling from the
 * attacker to the target.
 *
 * <p>Travel time is the distance divided by a fixed muzzle velocity, clamped
 * to {@code [0.06 s, 0.8 s]} per the effect specification. Tracers are kept
 * in insertion order and expired ones are compacted out, which keeps the
 * order deterministic.
 *
 * <p>Pure CPU: no OpenGL, fully testable.
 */
public final class TracerPool {

    /** Nominal shell velocity used for the travel-time estimate (m/s). */
    public static final double TRACER_SPEED_METRES_PER_SECOND = 600.0;

    /** Minimum tracer travel time in simulated seconds. */
    public static final double MIN_TRAVEL_SECONDS = 0.06;

    /** Maximum tracer travel time in simulated seconds. */
    public static final double MAX_TRAVEL_SECONDS = 0.8;

    private double[] sx = new double[16];
    private double[] sy = new double[16];
    private double[] sz = new double[16];
    private double[] ex = new double[16];
    private double[] ey = new double[16];
    private double[] ez = new double[16];
    private double[] t0 = new double[16];
    private double[] dur = new double[16];
    private int count = 0;

    /**
     * Travel time of a shell over the given distance, clamped to the
     * specification window {@code [0.06 s, 0.8 s]}.
     *
     * @param distance metres, finite and {@code >= 0}
     * @return the travel time in simulated seconds
     */
    public static double travelSeconds(double distance) {
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("distance must be finite and >= 0: " + distance);
        }
        double seconds = distance / TRACER_SPEED_METRES_PER_SECOND;
        return Math.min(MAX_TRAVEL_SECONDS, Math.max(MIN_TRAVEL_SECONDS, seconds));
    }

    /** @return the number of currently in-flight tracers */
    public int count() {
        return count;
    }

    /**
     * Registers one fired tracer.
     *
     * @param sx start x in metres (finite)
     * @param sy start y in metres (finite)
     * @param sz start z in metres (finite)
     * @param ex end x in metres (finite)
     * @param ey end y in metres (finite)
     * @param ez end z in metres (finite)
     * @param fireTime simulated seconds of the shot, finite and {@code >= 0}
     */
    public void add(double sx, double sy, double sz, double ex, double ey, double ez, double fireTime) {
        for (double v : new double[] { sx, sy, sz, ex, ey, ez }) {
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("tracer points must be finite, got: " + v);
            }
        }
        if (!Double.isFinite(fireTime) || fireTime < 0.0) {
            throw new IllegalArgumentException("fireTime must be finite and >= 0: " + fireTime);
        }
        if (count == this.sx.length) {
            grow();
        }
        this.sx[count] = sx;
        this.sy[count] = sy;
        this.sz[count] = sz;
        this.ex[count] = ex;
        this.ey[count] = ey;
        this.ez[count] = ez;
        this.t0[count] = fireTime;
        this.dur[count] = travelSeconds(distance(sx, sy, sz, ex, ey, ez));
        count++;
    }

    /**
     * @param index tracer index in {@code [0, count)}
     * @param time  simulated seconds
     * @return the travelled fraction in {@code [0, 1]}
     */
    public double progress(int index, double time) {
        checkIndex(index);
        if (!Double.isFinite(time)) {
            throw new IllegalArgumentException("time must be finite: " + time);
        }
        double p = (time - t0[index]) / dur[index];
        return Math.min(1.0, Math.max(0.0, p));
    }

    /** @param index tracer index @param time simulated seconds @return tip x */
    public double tipX(int index, double time) {
        checkIndex(index);
        double p = progress(index, time);
        return sx[index] + (ex[index] - sx[index]) * p;
    }

    /** @param index tracer index @param time simulated seconds @return tip y */
    public double tipY(int index, double time) {
        checkIndex(index);
        double p = progress(index, time);
        return sy[index] + (ey[index] - sy[index]) * p;
    }

    /** @param index tracer index @param time simulated seconds @return tip z */
    public double tipZ(int index, double time) {
        checkIndex(index);
        double p = progress(index, time);
        return sz[index] + (ez[index] - sz[index]) * p;
    }

    /**
     * @param index tracer index
     * @return the simulated second at which the shell lands
     */
    public double impactTime(int index) {
        checkIndex(index);
        return t0[index] + dur[index];
    }

    /** @param index tracer index @return the impact x in metres */
    public double endX(int index) { checkIndex(index); return ex[index]; }

    /** @param index tracer index @return the impact y in metres */
    public double endY(int index) { checkIndex(index); return ey[index]; }

    /** @param index tracer index @return the impact z in metres */
    public double endZ(int index) { checkIndex(index); return ez[index]; }

    /**
     * Removes every tracer whose impact time is not later than {@code time},
     * preserving the relative order of the remaining tracers.
     *
     * @param time simulated seconds
     * @return the number of removed tracers
     */
    public int removeExpired(double time) {
        if (!Double.isFinite(time)) {
            throw new IllegalArgumentException("time must be finite: " + time);
        }
        int w = 0;
        for (int r = 0; r < count; r++) {
            if (time < t0[r] + dur[r]) {
                if (w != r) {
                    sx[w] = sx[r];
                    sy[w] = sy[r];
                    sz[w] = sz[r];
                    ex[w] = ex[r];
                    ey[w] = ey[r];
                    ez[w] = ez[r];
                    t0[w] = t0[r];
                    dur[w] = dur[r];
                }
                w++;
            }
        }
        int removed = count - w;
        count = w;
        return removed;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("tracer index out of range: " + index);
        }
    }

    private static double distance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void grow() {
        int n = sx.length * 2;
        sx = java.util.Arrays.copyOf(sx, n);
        sy = java.util.Arrays.copyOf(sy, n);
        sz = java.util.Arrays.copyOf(sz, n);
        ex = java.util.Arrays.copyOf(ex, n);
        ey = java.util.Arrays.copyOf(ey, n);
        ez = java.util.Arrays.copyOf(ez, n);
        t0 = java.util.Arrays.copyOf(t0, n);
        dur = java.util.Arrays.copyOf(dur, n);
    }
}