package ua.battlesim.gfx.units.internal;

import java.util.Arrays;

/**
 * A growable, unboxed float buffer for accumulating per-frame vertex data on
 * the CPU. Deterministic and allocation-light (only grows when needed).
 */
public final class FloatList {

    private float[] data;
    private int size;

    public FloatList(int capacity) {
        if (capacity < 16) {
            capacity = 16;
        }
        this.data = new float[capacity];
    }

    public void add(float v) {
        if (size == data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        data[size++] = v;
    }

    /** @return the number of floats stored */
    public int size() {
        return size;
    }

    /** @return true if no floats are stored */
    public boolean isEmpty() {
        return size == 0;
    }

    /** @return a copy of the stored floats */
    public float[] toFloat() {
        return Arrays.copyOf(data, size);
    }

    public void clear() {
        size = 0;
    }
}
