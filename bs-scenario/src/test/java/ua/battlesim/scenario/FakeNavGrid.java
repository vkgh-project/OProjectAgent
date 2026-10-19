package ua.battlesim.scenario;

import ua.battlesim.api.NavGrid;

/**
 * A deterministic fake {@link NavGrid} for scenario tests: a square grid of
 * {@code cells x cells} cells over a {@code cells * cellSize} metre map with
 * an explicit set of blocked (impassable) cells. Every passable cell belongs
 * to the single main component; {@code walkable} is true exactly when the
 * whole circle of the given radius lies on the map and over passable cells.
 */
final class FakeNavGrid implements NavGrid {

    private final int cells;
    private final double cellSize;
    private final boolean[][] blocked; // blocked[j][i]
    private final int mainComponent;

    /**
     * @param cells         number of cells per side, {@code >= 1}
     * @param cellSize      the cell side length in metres, {@code > 0}
     * @param blocked       the blocked cells, {@code [j][i]} with both in range
     * @param mainComponent the id reported for every passable cell, {@code > 0}
     */
    FakeNavGrid(int cells, double cellSize, boolean[][] blocked, int mainComponent) {
        if (cells < 1 || cellSize <= 0.0 || mainComponent < 1) {
            throw new IllegalArgumentException("invalid grid: " + cells + " cells, "
                    + cellSize + " m, component " + mainComponent);
        }
        if (blocked.length != cells) {
            throw new IllegalArgumentException("blocked must have " + cells + " rows");
        }
        for (boolean[] row : blocked) {
            if (row.length != cells) {
                throw new IllegalArgumentException("blocked row must have " + cells + " columns");
            }
        }
        this.cells = cells;
        this.cellSize = cellSize;
        this.blocked = blocked;
        this.mainComponent = mainComponent;
    }

    @Override
    public int cells() {
        return cells;
    }

    @Override
    public double cellSize() {
        return cellSize;
    }

    @Override
    public boolean passable(int i, int j) {
        checkIndex(i, j);
        return !blocked[j][i];
    }

    @Override
    public int component(int i, int j) {
        checkIndex(i, j);
        return blocked[j][i] ? -1 : mainComponent;
    }

    @Override
    public int mainComponent() {
        return mainComponent;
    }

    @Override
    public boolean walkable(double x, double z, double radius) {
        if (!Double.isFinite(x) || !Double.isFinite(z)
                || !Double.isFinite(radius) || radius < 0.0) {
            return false;
        }
        double size = cells * cellSize;
        if (x - radius < 0.0 || x + radius > size
                || z - radius < 0.0 || z + radius > size) {
            return false;
        }
        int i0 = cell(x - radius);
        int j0 = cell(z - radius);
        int i1 = cell(x + radius);
        int j1 = cell(z + radius);
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                if (blocked[j][i]) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isRoad(double x, double z) {
        return false;
    }

    private int cell(double v) {
        int idx = (int) StrictMath.floor(v / cellSize);
        if (idx < 0) {
            return 0;
        }
        if (idx >= cells) {
            return cells - 1;
        }
        return idx;
    }

    private void checkIndex(int i, int j) {
        if (i < 0 || i >= cells || j < 0 || j >= cells) {
            throw new IllegalArgumentException(
                    "index (" + i + ", " + j + ") out of " + cells + "x" + cells);
        }
    }
}
