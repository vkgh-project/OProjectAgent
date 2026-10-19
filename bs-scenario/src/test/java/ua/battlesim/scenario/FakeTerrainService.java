package ua.battlesim.scenario;

import ua.battlesim.api.NavGrid;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.TerrainService;
import ua.battlesim.math.DetRandom;

/**
 * A deterministic fake {@link TerrainService} for scenario tests.
 *
 * <p>Every map is 2000 m with a 128 x 128 navigation grid (15.625 m cells).
 * For each seed two 8 x 8 cell "lake" patches are blocked at positions
 * derived from the seed (deterministic, so different seeds give different
 * layouts and placement is exercised), leaving the map mostly passable and
 * both spawn bands reachable.
 */
final class FakeTerrainService implements TerrainService {

    private static final int CELLS = 128;
    private static final double MAP_METRES = 2000.0;
    private static final int PATCH = 8;

    @Override
    public Terrain generate(long seed) {
        DetRandom r = new DetRandom(seed);
        boolean[][] blocked = new boolean[CELLS][CELLS];
        blockPatch(blocked, r);
        blockPatch(blocked, r);
        NavGrid grid = new FakeNavGrid(CELLS, MAP_METRES / CELLS, blocked, 1);
        return new FakeTerrain(MAP_METRES, grid);
    }

    private static void blockPatch(boolean[][] blocked, DetRandom r) {
        int col0 = PATCH + r.nextInt(CELLS - 2 * PATCH);
        int row0 = PATCH + r.nextInt(CELLS - 2 * PATCH);
        for (int j = row0; j < row0 + PATCH; j++) {
            for (int i = col0; i < col0 + PATCH; i++) {
                blocked[j][i] = true;
            }
        }
    }
}
