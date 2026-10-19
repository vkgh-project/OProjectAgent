package ua.battlesim.api;

/**
 * Discrete navigation grid over the map.
 *
 * <p>Layout: a {@code cells() x cells()} square; cell {@code (i, j)} covers
 * {@code [i*cellSize, (i+1)*cellSize) x [j*cellSize, (j+1)*cellSize)}.
 *
 * <p>Units: {@code cellSize} in metres; coordinates in metres (x east,
 * z south).
 *
 * <p>Deterministic: the same coordinates always map to the same cell.
 */
public interface NavGrid {

    /** @return the number of cells per row and column */
    int cells();

    /** @return the cell side length in metres */
    double cellSize();

    /** @return whether cell (i, j) is passable */
    boolean passable(int i, int j);

    /** @return the connected component id of cell (i, j) */
    int component(int i, int j);

    /** @return the component id of the largest connected passable area */
    int mainComponent();

    /**
     * @param x      position in metres
     * @param z      position in metres (south positive)
     * @param radius the unit radius in metres
     * @return whether a circle of the given radius around (x, z) fits on
     *         passable cells
     */
    boolean walkable(double x, double z, double radius);

    /** @return whether (x, z) in metres lies on a road */
    boolean isRoad(double x, double z);
}
