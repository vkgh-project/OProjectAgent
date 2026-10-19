package ua.battlesim.features;

import java.util.Arrays;
import java.util.Objects;

/**
 * A coarse boolean mask of the road corridor: the region around the road
 * centre lines where props (trees, rocks) must not be placed.
 *
 * <p>The mask is a {@code cells x cells} grid over the map, row-major
 * ({@code mask[j * cells + i]} for column {@code i}, row {@code j}); a cell is
 * {@code true} when it lies inside the corridor of any road.
 *
 * <p>Determinism and value semantics: two {@link RoadCorridor} with the same
 * map size, resolution and mask are equal.
 *
 * <p>Immutable and thread-safe.
 */
public final class RoadCorridor {

    private final double mapSize;
    private final int cells;
    private final boolean[] mask;

    /**
     * @param mapSize the side length of the square map in metres, finite, {@code > 0}
     * @param cells   the grid resolution (cells per map side), {@code >= 1}
     * @param mask    the row-major boolean mask, length exactly {@code cells * cells}
     * @throws IllegalArgumentException on an invalid argument
     */
    public RoadCorridor(double mapSize, int cells, boolean[] mask) {
        if (!Double.isFinite(mapSize) || mapSize <= 0.0) {
            throw new IllegalArgumentException("mapSize must be finite and > 0: " + mapSize);
        }
        if (cells < 1) {
            throw new IllegalArgumentException("cells must be >= 1: " + cells);
        }
        Objects.requireNonNull(mask, "mask");
        if (mask.length != cells * cells) {
            throw new IllegalArgumentException(
                    "mask length " + mask.length + " must equal cells*cells " + (cells * cells));
        }
        this.mapSize = mapSize;
        this.cells = cells;
        this.mask = mask.clone();
    }

    /** @return the side length of the square map in metres */
    public double mapSize() {
        return mapSize;
    }

    /** @return the grid resolution (cells per map side) */
    public int cells() {
        return cells;
    }

    /** @return the side length of one mask cell in metres */
    public double cellSize() {
        return mapSize / cells;
    }

    /**
     * @param i column in {@code [0, cells)}
     * @param j row in {@code [0, cells)}
     * @return {@code true} when that cell is inside the road corridor
     * @throws IllegalArgumentException if an index is out of range
     */
    public boolean at(int i, int j) {
        checkIndex(i, j);
        return mask[j * cells + i];
    }

    /**
     * @param x east-west position in metres, finite
     * @param z north-south position in metres (south positive), finite
     * @return {@code true} when (x, z) falls inside the road-corridor mask
     * @throws IllegalArgumentException if a coordinate is not finite
     */
    public boolean inCorridor(double x, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite: (" + x + ", " + z + ")");
        }
        double cs = cellSize();
        int ci = clampCell((int) (x / cs), cells);
        int cj = clampCell((int) (z / cs), cells);
        return mask[cj * cells + ci];
    }

    /** @return the number of cells marked inside the corridor */
    public int corridorCellCount() {
        int count = 0;
        for (boolean b : mask) {
            if (b) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RoadCorridor that)) {
            return false;
        }
        return mapSize == that.mapSize && cells == that.cells && Arrays.equals(mask, that.mask);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapSize, cells, Arrays.hashCode(mask));
    }

    private void checkIndex(int i, int j) {
        if (i < 0 || i >= cells || j < 0 || j >= cells) {
            throw new IllegalArgumentException("index (" + i + ", " + j + ") out of " + cells + "x" + cells);
        }
    }

    private static int clampCell(int v, int cells) {
        return v < 0 ? 0 : v >= cells ? cells - 1 : v;
    }
}