package ua.battlesim.features;

import org.junit.jupiter.api.Test;
import ua.battlesim.api.Bridge;
import ua.battlesim.api.HeightField;
import ua.battlesim.api.Prop;
import ua.battlesim.api.Road;
import ua.battlesim.api.Vec2;
import ua.battlesim.api.WaterField;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FeaturePlacer} using fakes of the neighbouring modules
 * (bs-api {@link HeightField} / {@link WaterField}).
 */
class FeaturePlacerTest {

    private static final double MAP = 2000.0;

    /**
     * Deterministic fake terrain: smooth heights with gentle slopes, a full-width
     * water band (z in [900, 1000]) and a swamp patch (x,z in [200, 400]).
     */
    static final class FakeTerrain implements HeightField, WaterField {
        private final double size;

        FakeTerrain(double size) {
            this.size = size;
        }

        @Override
        public double size() {
            return size;
        }

        @Override
        public double heightAt(double x, double z) {
            return 25.0 * StrictMath.sin(x / 180.0) + 12.0 * StrictMath.cos(z / 160.0);
        }

        @Override
        public double depthAt(double x, double z) {
            return (z >= 900.0 && z <= 1000.0) ? 1.5 : 0.0;
        }

        @Override
        public Vec2 flowAt(double x, double z) {
            return new Vec2(0.0, 0.0);
        }

        @Override
        public double swampAt(double x, double z) {
            return (x >= 200.0 && x <= 400.0 && z >= 200.0 && z <= 400.0) ? 0.8 : 0.0;
        }
    }

    private static TerrainFeatures place(long seed) {
        FakeTerrain fake = new FakeTerrain(MAP);
        return FeaturePlacer.place(fake, fake, seed);
    }

    @Test
    void mainRoadSpansNorthToSouth() {
        TerrainFeatures f = place(12345L);
        Road main = f.roads().get(0);
        assertEquals(6.5, main.width(), 1e-9, "main road width");
        assertEquals(0, main.kind(), "main road kind");
        Vec2 first = main.points().get(0);
        Vec2 last = main.points().get(main.points().size() - 1);
        assertTrue(first.z() < 0.10 * MAP, "main road should start near the north edge, z=" + first.z());
        assertTrue(last.z() > 0.90 * MAP, "main road should reach the south edge, z=" + last.z());
        assertTrue(main.points().size() >= 4, "main road should be resampled to several points");
        assertEquals(main.points().size(), main.onBridge().length, "onBridge length must match points");
    }

    @Test
    void noPropInsideRoadCorridor() {
        TerrainFeatures f = place(987654321L);
        RoadCorridor corridor = f.roadCorridor();
        for (Prop tree : f.trees()) {
            assertFalse(corridor.inCorridor(tree.x(), tree.z()),
                    "tree at (" + tree.x() + "," + tree.z() + ") lies inside the road corridor");
        }
        for (Prop rock : f.rocks()) {
            assertFalse(corridor.inCorridor(rock.x(), rock.z()),
                    "rock at (" + rock.x() + "," + rock.z() + ") lies inside the road corridor");
        }
    }

    @Test
    void deterministicOutput() {
        TerrainFeatures a = place(42L);
        TerrainFeatures b = place(42L);
        // bridges, trees, rocks and the corridor carry no raw arrays, so record
        // value-equality applies directly
        assertEquals(a.bridges(), b.bridges(), "bridges must be identical");
        assertEquals(a.trees(), b.trees(), "trees must be identical");
        assertEquals(a.rocks(), b.rocks(), "rocks must be identical");
        assertEquals(a.roadCorridor(), b.roadCorridor(), "road corridor must be identical");
        // roads contain a boolean[] (onBridge) whose record equals() is by
        // reference, so compare the flag arrays element-wise
        assertTrue(roadsEqual(a.roads(), b.roads()), "roads must be identical");

        // a different seed must change the layout
        TerrainFeatures c = place(1000003L);
        assertTrue(!roadsEqual(a.roads(), c.roads()) || !a.trees().equals(c.trees()),
                "a different seed should produce different features");
    }

    private static boolean roadsEqual(List<Road> x, List<Road> y) {
        if (x.size() != y.size()) {
            return false;
        }
        for (int i = 0; i < x.size(); i++) {
            Road rx = x.get(i);
            Road ry = y.get(i);
            if (Double.compare(rx.width(), ry.width()) != 0) {
                return false;
            }
            if (rx.kind() != ry.kind()) {
                return false;
            }
            if (!rx.points().equals(ry.points())) {
                return false;
            }
            if (!java.util.Arrays.equals(rx.onBridge(), ry.onBridge())) {
                return false;
            }
        }
        return true;
    }

    @Test
    void saneCountsAndBridges() {
        TerrainFeatures f = place(7L);

        int roads = f.roads().size();
        assertTrue(roads >= 5 && roads <= 9, "roads count out of range: " + roads);

        int trees = f.trees().size();
        assertTrue(trees >= 100, "too few trees: " + trees);
        assertTrue(trees <= 25000, "too many trees: " + trees);

        int rocks = f.rocks().size();
        assertTrue(rocks >= 3, "too few rocks: " + rocks);
        assertTrue(rocks <= 300, "too many rocks: " + rocks);

        // the main road crosses the full-width water band, so a bridge must exist
        assertFalse(f.bridges().isEmpty(), "expected a bridge over the water band");
        for (Bridge bridge : f.bridges()) {
            assertTrue(bridge.deckHeight() >= 0.0, "bridge deck height");
            assertTrue(bridge.width() > 0.0, "bridge width");
        }

        // two tree kinds (broadleaf 0 and conifer 1 at altitude)
        boolean hasBroadleaf = false;
        boolean hasConifer = false;
        for (Prop tree : f.trees()) {
            if (tree.kind() == 0) {
                hasBroadleaf = true;
            }
            if (tree.kind() == 1) {
                hasConifer = true;
            }
        }
        assertTrue(hasBroadleaf, "expected at least one broadleaf (kind 0)");
        assertTrue(hasConifer, "expected at least one conifer (kind 1)");

        // every road's bridge flags are consistent with its points
        for (Road road : f.roads()) {
            assertEquals(road.points().size(), road.onBridge().length, "onBridge length");
        }

        // the corridor is populated and covers a sane fraction of the map
        assertTrue(f.roadCorridor().corridorCellCount() > 0, "corridor should be populated");
        int total = f.roadCorridor().cells() * f.roadCorridor().cells();
        assertTrue(f.roadCorridor().corridorCellCount() < total, "corridor should not cover the whole map");
    }

    @Test
    void roadEndpointsStayOnMap() {
        TerrainFeatures f = place(555L);
        for (Road road : f.roads()) {
            for (Vec2 p : road.points()) {
                assertTrue(p.x() >= 0.0 && p.x() <= MAP, "road x in bounds: " + p.x());
                assertTrue(p.z() >= 0.0 && p.z() <= MAP, "road z in bounds: " + p.z());
            }
        }
    }
}