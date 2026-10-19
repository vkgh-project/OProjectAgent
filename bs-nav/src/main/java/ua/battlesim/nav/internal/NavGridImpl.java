package ua.battlesim.nav.internal;

import ua.battlesim.api.NavGrid;

/**
 * bs-api {@link NavGrid} view over the shared {@link GridModel}.
 *
 * <p>Layout: a 128x128 grid; cell {@code (i, j)} covers
 * {@code [i*cellSize, (i+1)*cellSize) x [j*cellSize, (j+1)*cellSize)}.
 */
final class NavGridImpl implements NavGrid {

    private final GridModel model;

    NavGridImpl(GridModel model) {
        this.model = model;
    }

    @Override
    public int cells() {
        return GridModel.CELLS;
    }

    @Override
    public double cellSize() {
        return model.cellSize;
    }

    @Override
    public boolean passable(int i, int j) {
        checkIndex(i, j);
        return model.passable[j * GridModel.CELLS + i];
    }

    @Override
    public int component(int i, int j) {
        checkIndex(i, j);
        return model.component[j * GridModel.CELLS + i];
    }

    @Override
    public int mainComponent() {
        return model.mainComponent;
    }

    @Override
    public boolean walkable(double x, double z, double radius) {
        return model.walkable(x, z, radius);
    }

    @Override
    public boolean isRoad(double x, double z) {
        return model.isRoad(x, z);
    }

    private static void checkIndex(int i, int j) {
        if (i < 0 || i >= GridModel.CELLS || j < 0 || j >= GridModel.CELLS) {
            throw new IllegalArgumentException("index (" + i + ", " + j + ") out of "
                    + GridModel.CELLS + "x" + GridModel.CELLS);
        }
    }
}
