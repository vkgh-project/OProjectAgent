package ua.battlesim.movement;

import java.util.HashMap;
import java.util.Map;

import ua.battlesim.api.NavGrid;
import ua.battlesim.api.Vec2;

/**
 * Test fake of the navigation grid: passable everywhere, single component by
 * default (individual cells can be moved to another component), optionally
 * a "road" flag, and an optional circular non-walkable pocket.
 */
final class FakeNavGrid implements NavGrid {

    private final int cells;
    private final double cellSize;
    private final Map<Long, Integer> components = new HashMap<>();
    private boolean road;
    private Vec2 pocket;
    private double pocketRadius;

    FakeNavGrid(int cells, double cellSize) {
        this.cells = cells;
        this.cellSize = cellSize;
    }

    /** Moves cell (i, j) to another component id. */
    void setComponent(int i, int j, int component) {
        components.put(key(i, j), component);
    }

    /** Makes the whole map a road (or removes the road). */
    void setRoad(boolean road) {
        this.road = road;
    }

    /** Marks a circular pocket as non-walkable. */
    void setPocket(Vec2 center, double radius) {
        this.pocket = center;
        this.pocketRadius = radius;
    }

    /** Removes the non-walkable pocket. */
    void clearPocket() {
        this.pocket = null;
    }

    private static long key(int i, int j) {
        return ((long) i << 32) | (j & 0xffffffffL);
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
        return true;
    }

    @Override
    public int component(int i, int j) {
        return components.getOrDefault(key(i, j), 0);
    }

    @Override
    public int mainComponent() {
        return 0;
    }

    @Override
    public boolean walkable(double x, double z, double radius) {
        return pocket == null || StrictMath.hypot(x - pocket.x(), z - pocket.z()) >= pocketRadius;
    }

    @Override
    public boolean isRoad(double x, double z) {
        return road;
    }
}