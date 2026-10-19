package ua.battlesim.nav;

import org.junit.jupiter.api.Test;
import ua.battlesim.api.Prop;
import ua.battlesim.api.Road;
import ua.battlesim.api.Vec2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grid rule tests for bs-nav using the {@link FakeTerrain} fake of the
 * neighbouring terrain module.
 */
class NavGridTest {

    private static final double MAP = 2000.0;
    private static final int CELLS = 128;

    static NavBundle flat() {
        return NavService.build(new FakeTerrain(MAP), new FakeTerrain(MAP));
    }

    @Test
    void flatMapIsFullyPassableInOneComponent() {
        var grid = flat().grid();
        assertEquals(CELLS, grid.cells());
        assertEquals(MAP / CELLS, grid.cellSize(), 1e-12);
        for (int j = 0; j < CELLS; j++) {
            for (int i = 0; i < CELLS; i++) {
                assertTrue(grid.passable(i, j), "flat cell (" + i + "," + j + ") must be passable");
            }
        }
        assertEquals(grid.component(0, 0), grid.mainComponent());
        assertEquals(grid.component(0, 0), grid.component(CELLS - 1, CELLS - 1));
        assertTrue(grid.walkable(1000, 1000, 2.0));
        assertFalse(grid.isRoad(1000, 1000));
    }

    @Test
    void deepWaterBlocksItsCellsExceptTheFallbackCorridor() {
        FakeTerrain t = new FakeTerrain(MAP).water(0, 905, 2000, 1095);
        var grid = NavService.build(t, t).grid();
        double cs = MAP / CELLS;
        for (int j = 0; j < CELLS; j++) {
            double cz = (j + 0.5) * cs;
            boolean inBand = cz >= 905.0 && cz <= 1095.0;
            for (int i = 0; i < CELLS; i++) {
                if (!inBand) {
                    assertTrue(grid.passable(i, j), "cell (" + i + "," + j + ") outside the water must be passable");
                } else {
                    boolean corridor = i >= CELLS / 2 - 1 && i <= CELLS / 2 + 1;
                    assertEquals(corridor, grid.passable(i, j),
                            "in-band cell (" + i + "," + j + "): only the fallback corridor may pass");
                }
            }
        }
    }

    @Test
    void roadCellsOverrideBlocking() {
        FakeTerrain t = new FakeTerrain(MAP).water(0, 905, 2000, 1095);
        Road road = new Road(java.util.List.of(new Vec2(1000, 0.0), new Vec2(1000, MAP)), 30.0, 1,
                new boolean[]{false, false});
        var grid = NavService.build(t, t, java.util.List.of(road), java.util.List.of(), java.util.List.of()).grid();
        double cs = MAP / CELLS;
        for (int j = 0; j < CELLS; j++) {
            double cz = (j + 0.5) * cs;
            boolean inBand = cz >= 905.0 && cz <= 1095.0;
            for (int i = 0; i < CELLS; i++) {
                double cx = (i + 0.5) * cs;
                boolean onRoad = StrictMath.abs(cx - 1000.0) <= 15.0;
                boolean expectPassable = !inBand || onRoad;
                assertEquals(expectPassable, grid.passable(i, j), "cell (" + i + "," + j + ")");
            }
        }
        assertTrue(grid.isRoad(1000, 950));
        assertFalse(grid.isRoad(50, 950));
    }

    @Test
    void swampBlocksItsCells() {
        FakeTerrain t = new FakeTerrain(MAP).swamp(400, 400, 600, 600);
        var grid = NavService.build(t, t).grid();
        double cs = MAP / CELLS;
        for (int j = 0; j < CELLS; j++) {
            double cz = (j + 0.5) * cs;
            boolean inSwampZ = cz >= 400.0 && cz <= 600.0;
            for (int i = 0; i < CELLS; i++) {
                double cx = (i + 0.5) * cs;
                boolean expectBlocked = inSwampZ && cx >= 400.0 && cx <= 600.0;
                assertEquals(expectBlocked, !grid.passable(i, j), "cell (" + i + "," + j + ")");
            }
        }
    }

    @Test
    void steepSlopeBlocksGentleSlopePasses() {
        // h = x + z -> slope atan(sqrt(2)) = 54.7 degrees > 32 degrees
        FakeTerrain steep = new FakeTerrain(MAP).slope(1.0, 1.0);
        var steepGrid = NavService.build(steep, steep).grid();
        assertFalse(steepGrid.passable(5, 5), "a 54.7 degree slope cell must be blocked");
        // the fallback corridor keeps a north-south route
        assertTrue(steepGrid.passable(64, 0));
        assertTrue(steepGrid.passable(64, 127));
        assertEquals(steepGrid.component(64, 0), steepGrid.component(64, 127));
        // h = 0.5 x -> slope atan(0.5) = 26.6 degrees < 32 degrees
        FakeTerrain gentle = new FakeTerrain(MAP).slope(0.5, 0.0);
        var gentleGrid = NavService.build(gentle, gentle).grid();
        assertTrue(gentleGrid.passable(5, 5), "a 26.6 degree slope cell must be passable");
        assertTrue(gentleGrid.passable(100, 60));
    }

    @Test
    void treeBlocksItsCellRockOnlyWhenLarge() {
        FakeTerrain t = new FakeTerrain(MAP);
        var treeGrid = NavService.build(t, t, java.util.List.of(),
                java.util.List.of(new Prop(700, 700, 1.0, 0.0, 0.5, 0)), java.util.List.of()).grid();
        assertFalse(treeGrid.passable(44, 44), "the tree cell must be blocked");
        assertTrue(treeGrid.passable(45, 45));

        var smallRock = NavService.build(t, t, java.util.List.of(), java.util.List.of(),
                java.util.List.of(new Prop(700, 700, 1.0, 0.0, 0.5, 0))).grid();
        assertTrue(smallRock.passable(44, 44), "a rock with scale < 1.4 must not block");

        var bigRock = NavService.build(t, t, java.util.List.of(), java.util.List.of(),
                java.util.List.of(new Prop(700, 700, 1.5, 0.0, 0.5, 0))).grid();
        assertFalse(bigRock.passable(44, 44), "a rock with scale >= 1.4 must block");
    }

    @Test
    void fallbackCorridorGuaranteesNorthSouthRoute() {
        FakeTerrain t = new FakeTerrain(MAP).water(0, 905, 2000, 1095);
        var grid = NavService.build(t, t).grid();
        assertTrue(grid.walkable(1000, 50, 2.0), "a corridor cell north of the water must be walkable");
        assertTrue(grid.walkable(1000, 1950, 2.0), "a corridor cell south of the water must be walkable");
        assertEquals(grid.component(64, 0), grid.component(64, 127),
                "the corridor must connect the north and south quarters");
        assertEquals(grid.component(64, 0), grid.mainComponent(),
                "the corridor lies in the main component");
        assertFalse(grid.passable(10, 64), "water rows away from the corridor stay blocked");
        assertFalse(grid.passable(117, 64));
    }

    @Test
    void walkableChecksCircleClearanceAndBounds() {
        FakeTerrain t = new FakeTerrain(MAP).water(0, 905, 2000, 1095);
        var grid = NavService.build(t, t).grid();
        double cs = MAP / CELLS;
        assertTrue(grid.walkable(7.8125, 7.8125, 1.0), "centre of a passable cell is walkable");
        assertFalse(grid.walkable(7.8125, 64.5 * cs, 1.0), "centre of a blocked water cell is not walkable");
        assertFalse(grid.walkable(-1.0, 1000.0, 2.0), "off the west edge is not walkable");
        assertFalse(grid.walkable(1000.0, -1.0, 2.0), "off the north edge is not walkable");
        assertFalse(grid.walkable(2001.0, 1000.0, 2.0), "off the east edge is not walkable");
        assertThrows(IllegalArgumentException.class, () -> grid.walkable(1000, 1000, -1.0));
        assertThrows(IllegalArgumentException.class, () -> grid.walkable(Double.NaN, 1000, 2.0));
    }

    @Test
    void indexQueriesValidateBounds() {
        var grid = flat().grid();
        assertThrows(IllegalArgumentException.class, () -> grid.passable(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> grid.passable(CELLS, 0));
        assertThrows(IllegalArgumentException.class, () -> grid.component(0, -1));
        assertThrows(IllegalArgumentException.class, () -> grid.component(0, CELLS));
        assertThrows(IllegalArgumentException.class, () -> grid.isRoad(Double.NaN, 0.0));
    }
}