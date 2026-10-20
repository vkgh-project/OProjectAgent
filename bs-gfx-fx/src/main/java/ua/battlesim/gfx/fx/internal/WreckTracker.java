package ua.battlesim.gfx.fx.internal;

/**
 * Tracks destroyed units and when each wreck becomes <em>visibly</em>
 * destroyed: a wreck starts emitting continuous rising smoke and fire only
 * after the last incoming killing shell has landed, and does so for the
 * first 90 simulated seconds after becoming visible.
 *
 * <p>Killing impacts arrive as {@code AttackEvent(killed = true)} (their
 * visual landing is the tracer impact time) and the destruction itself as a
 * {@code UnitDestroyedEvent}. Either order is handled; the visibility time
 * is the maximum of the event time and all recorded killing impact times.
 *
 * <p>Entries are kept sorted by unit id (ascending), so iteration and
 * spawning order never depends on hash order.
 *
 * <p>Pure CPU: no OpenGL, fully testable.
 */
public final class WreckTracker {

    /** How long a visible wreck keeps emitting smoke and fire (simulated seconds). */
    public static final double EFFECT_SECONDS = 90.0;

    /** How long after the effect ends a wreck is kept (grace for live particles). */
    public static final double PRUNE_GRACE_SECONDS = 10.0;

    private int count = 0;
    private int[] ids = new int[16];
    private double[] xs = new double[16];
    private double[] ys = new double[16];
    private double[] zs = new double[16];
    private double[] destroyedAt = new double[16];
    private double[] visibleAt = new double[16];
    private double[] smokeAcc = new double[16];
    private double[] fireAcc = new double[16];

    // Killing impacts recorded before the UnitDestroyedEvent arrived.
    private int pendCount = 0;
    private int[] pendIds = new int[16];
    private double[] pendTimes = new double[16];

    /** @return the number of tracked wrecks */
    public int count() {
        return count;
    }

    /** @param i wreck index in {@code [0, count)} @return the unit id (ascending order) */
    public int id(int i) {
        checkIndex(i);
        return ids[i];
    }

    /** @param i wreck index @return wreck x in metres */
    public double x(int i) { checkIndex(i); return xs[i]; }

    /** @param i wreck index @return wreck y in metres */
    public double y(int i) { checkIndex(i); return ys[i]; }

    /** @param i wreck index @return wreck z in metres */
    public double z(int i) { checkIndex(i); return zs[i]; }

    /**
     * @param id the destroyed unit id
     * @return true if that unit is tracked as a wreck
     */
    public boolean known(int id) {
        return find(id) >= 0;
    }

    /**
     * @param id the destroyed unit id
     * @return the simulated second from which the wreck is visible
     */
    public double visibleAt(int id) {
        return visibleAt[find(id)];
    }

    /**
     * @param id the destroyed unit id
     * @param t  simulated seconds
     * @return true if the wreck is visibly destroyed at {@code t}
     */
    public boolean visible(int id, double t) {
        int i = find(id);
        return i >= 0 && t >= visibleAt[i];
    }

    /**
     * @param id the destroyed unit id
     * @return the simulated second until which the wreck emits smoke and fire
     */
    public double effectEnd(int id) {
        return visibleAt(id) + EFFECT_SECONDS;
    }

    /** @param i wreck index @return the current smoke spawn accumulator */
    public double smokeAcc(int i) { checkIndex(i); return smokeAcc[i]; }

    /** @param i wreck index @param value the new smoke spawn accumulator */
    public void setSmokeAcc(int i, double value) { checkIndex(i); smokeAcc[i] = value; }

    /** @param i wreck index @return the current fire spawn accumulator */
    public double fireAcc(int i) { checkIndex(i); return fireAcc[i]; }

    /** @param i wreck index @param value the new fire spawn accumulator */
    public void setFireAcc(int i, double value) { checkIndex(i); fireAcc[i] = value; }

    /**
     * Records a {@code UnitDestroyedEvent} for the given unit.
     *
     * @param id          the destroyed unit id, {@code >= 0}
     * @param x           wreck x in metres (finite)
     * @param y           wreck y in metres (finite)
     * @param z           wreck z in metres (finite)
     * @param eventTime   simulated seconds of the event
     */
    public void noteDestroyed(int id, double x, double y, double z, double eventTime) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0: " + id);
        }
        for (double v : new double[] { x, y, z, eventTime }) {
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("wreck fields must be finite, got: " + v);
            }
        }
        if (eventTime < 0.0) {
            throw new IllegalArgumentException("eventTime must be >= 0: " + eventTime);
        }
        int i = find(id);
        if (i >= 0) {
            // Re-announced: keep the worst (latest) visibility time.
            destroyedAt[i] = Math.max(destroyedAt[i], eventTime);
            visibleAt[i] = Math.max(visibleAt[i], eventTime);
            return;
        }
        if (count == ids.length) {
            grow();
        }
        int pos = count;
        while (pos > 0 && ids[pos - 1] > id) {
            pos--;
        }
        for (int k = count; k > pos; k--) {
            ids[k] = ids[k - 1];
            xs[k] = xs[k - 1];
            ys[k] = ys[k - 1];
            zs[k] = zs[k - 1];
            destroyedAt[k] = destroyedAt[k - 1];
            visibleAt[k] = visibleAt[k - 1];
            smokeAcc[k] = smokeAcc[k - 1];
            fireAcc[k] = fireAcc[k - 1];
        }
        ids[pos] = id;
        xs[pos] = x;
        ys[pos] = y;
        zs[pos] = z;
        destroyedAt[pos] = eventTime;
        visibleAt[pos] = Math.max(eventTime, pendTime(id));
        count++;
    }

    /**
     * Records a killing shell impact ({@code AttackEvent} with
     * {@code killed = true}) for the target unit: the wreck becomes visible
     * no earlier than the impact time, even if the destruction event already
     * arrived.
     *
     * @param id         the target unit id, {@code >= 0}
     * @param impactTime simulated seconds at which the shell lands
     */
    public void noteKillingImpact(int id, double impactTime) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0: " + id);
        }
        if (!Double.isFinite(impactTime) || impactTime < 0.0) {
            throw new IllegalArgumentException("impactTime must be finite and >= 0: " + impactTime);
        }
        int i = find(id);
        if (i >= 0) {
            visibleAt[i] = Math.max(visibleAt[i], impactTime);
            return;
        }
        // The destruction event has not arrived yet: remember the impact so
        // the later noteDestroyed call starts the wreck at this time.
        int p = pendFind(id);
        if (p >= 0) {
            pendTimes[p] = Math.max(pendTimes[p], impactTime);
            return;
        }
        if (pendCount == pendIds.length) {
            pendIds = java.util.Arrays.copyOf(pendIds, pendIds.length * 2);
            pendTimes = java.util.Arrays.copyOf(pendTimes, pendTimes.length * 2);
        }
        pendIds[pendCount] = id;
        pendTimes[pendCount] = impactTime;
        pendCount++;
    }

    private int pendFind(int id) {
        for (int p = 0; p < pendCount; p++) {
            if (pendIds[p] == id) {
                return p;
            }
        }
        return -1;
    }

    private double pendTime(int id) {
        int p = pendFind(id);
        return p < 0 ? 0.0 : pendTimes[p];
    }

    /**
     * Drops wrecks whose smoke and fire effect ended more than
     * {@link #PRUNE_GRACE_SECONDS} ago, in ascending id order.
     *
     * @param time simulated seconds
     * @return the number of removed wrecks
     */
    public int prune(double time) {
        if (!Double.isFinite(time)) {
            throw new IllegalArgumentException("time must be finite: " + time);
        }
        int w = 0;
        for (int r = 0; r < count; r++) {
            if (time < visibleAt[r] + EFFECT_SECONDS + PRUNE_GRACE_SECONDS) {
                if (w != r) {
                    ids[w] = ids[r];
                    xs[w] = xs[r];
                    ys[w] = ys[r];
                    zs[w] = zs[r];
                    destroyedAt[w] = destroyedAt[r];
                    visibleAt[w] = visibleAt[r];
                    smokeAcc[w] = smokeAcc[r];
                    fireAcc[w] = fireAcc[r];
                }
                w++;
            }
        }
        int removed = count - w;
        count = w;
        return removed;
    }

    /**
     * A deterministic checksum over the tracked wrecks, for determinism
     * tests.
     */
    public long fingerprint() {
        long h = 0xC0FFEEC0FFEEC0FFL;
        for (int i = 0; i < count; i++) {
            h ^= ids[i] + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
            h ^= Double.doubleToLongBits(visibleAt[i]) ^ (h << 13) ^ (h >>> 7);
            h ^= Double.doubleToLongBits(smokeAcc[i]) ^ (h << 11) ^ (h >>> 5);
            h ^= Double.doubleToLongBits(fireAcc[i]) ^ (h << 17) ^ (h >>> 3);
        }
        return h;
    }

    private int find(int id) {
        int lo = 0;
        int hi = count - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (ids[mid] < id) {
                lo = mid + 1;
            } else if (ids[mid] > id) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        return -1;
    }

    private void checkIndex(int i) {
        if (i < 0 || i >= count) {
            throw new IndexOutOfBoundsException("wreck index out of range: " + i);
        }
    }

    private void grow() {
        int n = ids.length * 2;
        ids = java.util.Arrays.copyOf(ids, n);
        xs = java.util.Arrays.copyOf(xs, n);
        ys = java.util.Arrays.copyOf(ys, n);
        zs = java.util.Arrays.copyOf(zs, n);
        destroyedAt = java.util.Arrays.copyOf(destroyedAt, n);
        visibleAt = java.util.Arrays.copyOf(visibleAt, n);
        smokeAcc = java.util.Arrays.copyOf(smokeAcc, n);
        fireAcc = java.util.Arrays.copyOf(fireAcc, n);
    }
}