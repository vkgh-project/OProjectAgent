package ua.battlesim.nav.internal;

/**
 * Binary min-heap of A* open-list entries {@code (f, cell, seq)} ordered by
 * f, then cell index, then insertion sequence number. The total order makes
 * the pop order deterministic, so the search never depends on container
 * internals (no {@code PriorityQueue} tie ambiguity, no hash order).
 */
final class OpenHeap {

    private double[] f;
    private int[] cell;
    private long[] seq;
    private int size;

    OpenHeap(int initialCapacity) {
        if (initialCapacity < 4) {
            initialCapacity = 4;
        }
        f = new double[initialCapacity];
        cell = new int[initialCapacity];
        seq = new long[initialCapacity];
    }

    boolean isEmpty() {
        return size == 0;
    }

    void push(double fValue, int c, long s) {
        if (size == f.length) {
            grow();
        }
        int i = size++;
        f[i] = fValue;
        cell[i] = c;
        seq[i] = s;
        siftUp(i);
    }

    /** @return the cell of the minimal entry */
    int pop() {
        int topCell = cell[0];
        size--;
        if (size > 0) {
            f[0] = f[size];
            cell[0] = cell[size];
            seq[0] = seq[size];
            siftDown(0);
        }
        return topCell;
    }

    private void grow() {
        int n = f.length * 2;
        double[] nf = new double[n];
        int[] nc = new int[n];
        long[] ns = new long[n];
        System.arraycopy(f, 0, nf, 0, f.length);
        System.arraycopy(cell, 0, nc, 0, cell.length);
        System.arraycopy(seq, 0, ns, 0, seq.length);
        f = nf;
        cell = nc;
        seq = ns;
    }

    private boolean less(int a, int b) {
        if (f[a] != f[b]) {
            return f[a] < f[b];
        }
        if (cell[a] != cell[b]) {
            return cell[a] < cell[b];
        }
        return seq[a] < seq[b];
    }

    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (!less(i, parent)) {
                return;
            }
            swap(i, parent);
            i = parent;
        }
    }

    private void siftDown(int i) {
        int n = size;
        while (true) {
            int left = 2 * i + 1;
            if (left >= n) {
                return;
            }
            int right = left + 1;
            int best = left;
            if (right < n && less(right, left)) {
                best = right;
            }
            if (!less(best, i)) {
                return;
            }
            swap(best, i);
            i = best;
        }
    }

    private void swap(int a, int b) {
        double tf = f[a];
        f[a] = f[b];
        f[b] = tf;
        int tc = cell[a];
        cell[a] = cell[b];
        cell[b] = tc;
        long ts = seq[a];
        seq[a] = seq[b];
        seq[b] = ts;
    }
}
