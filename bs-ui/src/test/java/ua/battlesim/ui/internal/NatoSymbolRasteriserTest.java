package ua.battlesim.ui.internal;

import org.junit.jupiter.api.Test;
import ua.battlesim.api.Side;
import ua.battlesim.api.UnitType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link NatoSymbolRasteriser} — verifies NATO symbol shapes
 * without a GPU.
 */
class NatoSymbolRasteriserTest {

    private static final int S = NatoSymbolRasteriser.SIZE;

    @Test
    void rectangleBorder_coversEdges() {
        assertTrue(NatoSymbolRasteriser.isRectangleBorderPixel(0, 0));
        assertTrue(NatoSymbolRasteriser.isRectangleBorderPixel(10, 0));
        assertTrue(NatoSymbolRasteriser.isRectangleBorderPixel(10, S - 1));
        assertTrue(NatoSymbolRasteriser.isRectangleBorderPixel(0, 10));
        assertTrue(NatoSymbolRasteriser.isRectangleBorderPixel(S - 1, 10));
        assertFalse(NatoSymbolRasteriser.isRectangleBorderPixel(15, 15));
        assertFalse(NatoSymbolRasteriser.isRectangleBorderPixel(16, 16));
    }

    @Test
    void rectangleBorder_thickness() {
        assertTrue(NatoSymbolRasteriser.isRectangleBorderPixel(15, 0));
        assertTrue(NatoSymbolRasteriser.isRectangleBorderPixel(15, 1));
        assertFalse(NatoSymbolRasteriser.isRectangleBorderPixel(15, 3));
    }

    @Test
    void diamondBorder_coversCorners() {
        assertTrue(NatoSymbolRasteriser.isDiamondBorderPixel(15, 0));
        assertTrue(NatoSymbolRasteriser.isDiamondBorderPixel(16, 0));
        assertTrue(NatoSymbolRasteriser.isDiamondBorderPixel(15, S - 1));
        assertTrue(NatoSymbolRasteriser.isDiamondBorderPixel(0, 15));
        assertTrue(NatoSymbolRasteriser.isDiamondBorderPixel(S - 1, 15));
    }

    @Test
    void diamondBorder_centerNotInBorder() {
        assertFalse(NatoSymbolRasteriser.isDiamondBorderPixel(15, 15));
        assertFalse(NatoSymbolRasteriser.isDiamondBorderPixel(16, 16));
    }

    @Test
    void diamondBorder_cornersAreEmpty() {
        assertFalse(NatoSymbolRasteriser.isDiamondBorderPixel(0, 0));
        assertFalse(NatoSymbolRasteriser.isDiamondBorderPixel(S - 1, 0));
        assertFalse(NatoSymbolRasteriser.isDiamondBorderPixel(0, S - 1));
        assertFalse(NatoSymbolRasteriser.isDiamondBorderPixel(S - 1, S - 1));
    }

    @Test
    void tankOval_centerIsInside() {
        assertTrue(NatoSymbolRasteriser.isTankOvalPixel(15, 15));
        assertTrue(NatoSymbolRasteriser.isTankOvalPixel(16, 16));
    }

    @Test
    void tankOval_extent() {
        assertTrue(NatoSymbolRasteriser.isTankOvalPixel(15 + 7, 15));
        assertTrue(NatoSymbolRasteriser.isTankOvalPixel(15 + 8, 15));
        assertFalse(NatoSymbolRasteriser.isTankOvalPixel(15 + 10, 15));
        assertTrue(NatoSymbolRasteriser.isTankOvalPixel(15, 15 + 5));
        assertFalse(NatoSymbolRasteriser.isTankOvalPixel(15, 15 + 8));
    }

    @Test
    void tankOval_outOfBounds() {
        assertFalse(NatoSymbolRasteriser.isTankOvalPixel(-1, 0));
        assertFalse(NatoSymbolRasteriser.isTankOvalPixel(0, -1));
        assertFalse(NatoSymbolRasteriser.isTankOvalPixel(S, 0));
        assertFalse(NatoSymbolRasteriser.isTankOvalPixel(0, S));
    }
    @Test
    void rasterise_friendlyTank_hasRectangleBorder() {
        boolean[][] grid = NatoSymbolRasteriser.rasterise(Side.BLUE, UnitType.TANK);
        assertEquals(S, grid.length);
        assertEquals(S, grid[0].length);
        assertTrue(grid[0][0]);
        assertTrue(grid[15][15]);
    }

    @Test
    void rasterise_hostileTank_hasDiamondBorder() {
        boolean[][] grid = NatoSymbolRasteriser.rasterise(Side.RED, UnitType.TANK);
        assertTrue(grid[0][15]);
        assertTrue(grid[0][16]);
        assertFalse(grid[0][0]);
        assertFalse(grid[0][S - 1]);
    }

    @Test
    void rasterise_friendlyInfantry_hasCross() {
        boolean[][] grid = NatoSymbolRasteriser.rasterise(Side.BLUE, UnitType.INFANTRY);
        assertTrue(grid[15][15]);
        assertTrue(grid[15][16]);
        assertTrue(grid[16][15]);
        assertTrue(grid[16][16]);
        assertTrue(grid[15][8]);
        assertTrue(grid[8][15]);
    }

    @Test
    void rasterise_hostileInfantry_hasDiamondAndCross() {
        boolean[][] grid = NatoSymbolRasteriser.rasterise(Side.RED, UnitType.INFANTRY);
        assertTrue(grid[0][15]);
        assertTrue(grid[15][15]);
    }

    @Test
    void rasterise_friendlyApc_hasCrossAndOval() {
        boolean[][] grid = NatoSymbolRasteriser.rasterise(Side.BLUE, UnitType.APC_INFANTRY);
        assertTrue(grid[15][15]);
        assertTrue(grid[15][15 + 4]);
        assertTrue(grid[15 + 4][15]);
    }

    @Test
    void rasterise_hostileApc_hasDiamond() {
        boolean[][] grid = NatoSymbolRasteriser.rasterise(Side.RED, UnitType.APC_INFANTRY);
        assertTrue(grid[0][15]);
        assertFalse(grid[0][0]);
    }

    @Test
    void rasterise_nullSide_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> NatoSymbolRasteriser.rasterise(null, UnitType.TANK));
    }

    @Test
    void rasterise_nullType_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> NatoSymbolRasteriser.rasterise(Side.BLUE, null));
    }

    @Test
    void rasterise_deterministic() {
        boolean[][] g1 = NatoSymbolRasteriser.rasterise(Side.BLUE, UnitType.TANK);
        boolean[][] g2 = NatoSymbolRasteriser.rasterise(Side.BLUE, UnitType.TANK);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                assertEquals(g1[y][x], g2[y][x], "Mismatch at (" + x + ", " + y + ")");
            }
        }
    }
}
