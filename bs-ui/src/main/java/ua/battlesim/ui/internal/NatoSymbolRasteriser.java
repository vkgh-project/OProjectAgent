package ua.battlesim.ui.internal;

import ua.battlesim.api.Side;
import ua.battlesim.api.UnitType;

/**
 * Rasterises NATO tactical symbols into a 2D boolean pixel grid.
 *
 * <p>The output grid is {@value SIZE} x {@value SIZE} pixels.
 * {@code true} indicates a filled pixel; {@code false} is background.
 *
 * <p>NATO symbol conventions:
 * <ul>
 *   <li>Friendly (BLUE): rectangle border</li>
 *   <li>Hostile (RED): diamond border</li>
 *   <li>TANK (brigade): oval + X</li>
 *   <li>INFANTRY (company): cross + I</li>
 *   <li>APC_INFANTRY (battalion): cross + oval + II</li>
 * </ul>
 *
 * <p>Deterministic: no randomness, no wall clock.
 *
 * <p>Thread-safe: all methods are static and stateless.
 */
public final class NatoSymbolRasteriser {

    /** The rasteriser output grid size in pixels (square). */
    public static final int SIZE = 32;

    /** Border thickness in pixels. */
    private static final int BORDER_THICKNESS = 2;

    /** Center of the grid (half of SIZE-1). */
    private static final double CENTER = (SIZE - 1) / 2.0;

    private NatoSymbolRasteriser() {}

    /**
     * Rasterises a NATO symbol for the given side and unit type.
     *
     * @param side the side (BLUE = friendly/rectangle border, RED = hostile/diamond border)
     * @param type the unit type (TANK, INFANTRY, APC_INFANTRY)
     * @return a {@code SIZE x SIZE} boolean grid where {@code true} = filled pixel
     * @throws IllegalArgumentException if side or type is null
     */
    public static boolean[][] rasterise(Side side, UnitType type) {
        if (side == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        boolean[][] grid = new boolean[SIZE][SIZE];

        if (side == Side.BLUE) {
            drawRectangleBorder(grid);
        } else {
            drawDiamondBorder(grid);
        }

        switch (type) {
            case TANK -> drawTankSymbol(grid);
            case INFANTRY -> drawInfantrySymbol(grid);
            case APC_INFANTRY -> drawApcSymbol(grid);
        }

        return grid;
    }

    /**
     * Checks if the given pixel is part of the rectangle border.
     *
     * @param px pixel x (0-based, 0 to SIZE-1)
     * @param py pixel y (0-based, 0 to SIZE-1)
     * @return true if the pixel is in the rectangle border
     */
    public static boolean isRectangleBorderPixel(int px, int py) {
        if (px < 0 || px >= SIZE || py < 0 || py >= SIZE) {
            return false;
        }
        return py < BORDER_THICKNESS
                || py >= SIZE - BORDER_THICKNESS
                || px < BORDER_THICKNESS
                || px >= SIZE - BORDER_THICKNESS;
    }

    /**
     * Checks if the given pixel is part of the diamond border.
     *
     * @param px pixel x (0-based, 0 to SIZE-1)
     * @param py pixel y (0-based, 0 to SIZE-1)
     * @return true if the pixel is in the diamond border
     */
    public static boolean isDiamondBorderPixel(int px, int py) {
        if (px < 0 || px >= SIZE || py < 0 || py >= SIZE) {
            return false;
        }
        double dist = Math.abs(px - CENTER) + Math.abs(py - CENTER);
        double outerRadius = SIZE / 2.0;
        double innerRadius = SIZE / 2.0 - BORDER_THICKNESS;
        return dist <= outerRadius && dist > innerRadius;
    }

    /**
     * Checks if the given pixel is inside the tank oval symbol.
     *
     * @param px pixel x (0-based)
     * @param py pixel y (0-based)
     * @return true if the pixel is inside the oval
     */
    public static boolean isTankOvalPixel(int px, int py) {
        if (px < 0 || px >= SIZE || py < 0 || py >= SIZE) {
            return false;
        }
        double dx = (px - CENTER) / 8.0;
        double dy = (py - CENTER) / 6.0;
        return dx * dx + dy * dy <= 1.0;
    }

    private static void drawRectangleBorder(boolean[][] grid) {
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                if (isRectangleBorderPixel(px, py)) {
                    grid[py][px] = true;
                }
            }
        }
    }

    private static void drawDiamondBorder(boolean[][] grid) {
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                if (isDiamondBorderPixel(px, py)) {
                    grid[py][px] = true;
                }
            }
        }
    }

    private static void drawTankSymbol(boolean[][] grid) {
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                double dx = (px - CENTER) / 8.0;
                double dy = (py - CENTER) / 6.0;
                if (dx * dx + dy * dy <= 1.0) {
                    grid[py][px] = true;
                }
            }
        }
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                double dx = (px - CENTER) / 8.0;
                double dy = (py - CENTER) / 6.0;
                if (dx * dx + dy * dy > 1.0) continue;
                if (Math.abs((px - CENTER) - (py - CENTER)) <= 1.5) {
                    grid[py][px] = true;
                }
                if (Math.abs((px - CENTER) + (py - CENTER)) <= 1.5) {
                    grid[py][px] = true;
                }
            }
        }
    }

    private static void drawInfantrySymbol(boolean[][] grid) {
        for (int py = 5; py <= 26; py++) {
            for (int px = 5; px <= 26; px++) {
                if (Math.abs(px - CENTER) <= 1.5) {
                    grid[py][px] = true;
                }
                if (Math.abs(py - CENTER) <= 1.5) {
                    grid[py][px] = true;
                }
            }
        }
        for (int px = 11; px <= 20; px++) {
            grid[SIZE - 8][px] = true;
            grid[SIZE - 7][px] = true;
        }
    }

    private static void drawApcSymbol(boolean[][] grid) {
        for (int py = 5; py <= 26; py++) {
            for (int px = 5; px <= 26; px++) {
                if (Math.abs(px - CENTER) <= 1.5) {
                    grid[py][px] = true;
                }
                if (Math.abs(py - CENTER) <= 1.5) {
                    grid[py][px] = true;
                }
            }
        }
        for (int py = 0; py < SIZE; py++) {
            for (int px = 0; px < SIZE; px++) {
                double dx = (px - CENTER) / 5.0;
                double dy = (py - CENTER) / 4.0;
                if (dx * dx + dy * dy <= 1.0) {
                    grid[py][px] = true;
                }
            }
        }
        for (int px = 8; px <= 13; px++) {
            grid[SIZE - 8][px] = true;
            grid[SIZE - 7][px] = true;
        }
        for (int px = 18; px <= 23; px++) {
            grid[SIZE - 8][px] = true;
            grid[SIZE - 7][px] = true;
        }
    }
}