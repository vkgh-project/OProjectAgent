package ua.battlesim.nav;

import org.junit.jupiter.api.Test;
import ua.battlesim.api.Prop;
import ua.battlesim.api.Vec2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Line-of-sight tests for bs-nav using the {@link FakeTerrain} fake and
 * bs-api {@link Prop} trees.
 */
class LineOfSightTest {

    private static final double MAP = 2000.0;

    static NavBundle withTrees(Prop... trees) {
        FakeTerrain t = new FakeTerrain(MAP);
        return NavService.build(t, t, java.util.List.of(), java.util.List.of(trees), java.util.List.of());
    }

    @Test
    void flatOpenLineIsVisible() {
        FakeTerrain t = new FakeTerrain(MAP);
        var los = NavService.build(t, t).lineOfSight();
        assertTrue(los.visible(new Vec2(400, 1000), 2.0, new Vec2(1600, 1000), 2.0));
    }

    @Test
    void hillBlocksLineOfSight() {
        FakeTerrain t = new FakeTerrain(MAP).hill(60.0, 1000, 1000, 150.0);
        var los = NavService.build(t, t).lineOfSight();
        assertFalse(los.visible(new Vec2(400, 1000), 2.0, new Vec2(1600, 1000), 2.0),
                "a 60 m hill on the sight line must block it");
    }

    @Test
    void gentleSlopeDoesNotBlock() {
        FakeTerrain t = new FakeTerrain(MAP).slope(0.1, 0.0);
        var los = NavService.build(t, t).lineOfSight();
        assertTrue(los.visible(new Vec2(200, 1000), 2.0, new Vec2(1800, 1000), 2.0),
                "the sight line stays above a linear slope");
    }

    @Test
    void treeBlocksLineOfSightWithinTenMetres() {
        var los = withTrees(new Prop(1000, 500, 1.0, 0.0, 0.5, 0)).lineOfSight();
        assertFalse(los.visible(new Vec2(500, 500), 2.0, new Vec2(1500, 500), 2.0),
                "a tree on the sight line must block it");
        assertTrue(los.visible(new Vec2(500, 500), 2.0, new Vec2(1500, 520), 2.0),
                "a sight line 10 m from the tree must stay visible");
    }

    @Test
    void treesAtTheEndpointsAreIgnored() {
        var losFrom = withTrees(new Prop(500, 500, 1.0, 0.0, 0.5, 0)).lineOfSight();
        assertTrue(losFrom.visible(new Vec2(500, 500), 2.0, new Vec2(1500, 500), 2.0),
                "a tree at the observer position must not block (first 10 m ignored)");
        var losTo = withTrees(new Prop(1500, 500, 1.0, 0.0, 0.5, 0)).lineOfSight();
        assertTrue(losTo.visible(new Vec2(500, 500), 2.0, new Vec2(1500, 500), 2.0),
                "a tree at the target position must not block (last 10 m ignored)");
    }

    @Test
    void shortSegmentIsAlwaysVisible() {
        var los = withTrees(new Prop(1002, 500, 1.0, 0.0, 0.5, 0)).lineOfSight();
        assertTrue(los.visible(new Vec2(1000, 500), 2.0, new Vec2(1005, 500), 2.0),
                "a 5 m segment lies entirely inside the ignored ends");
    }

    @Test
    void invalidArgumentsAreRejected() {
        FakeTerrain t = new FakeTerrain(MAP);
        var los = NavService.build(t, t).lineOfSight();
        assertThrows(NullPointerException.class,
                () -> los.visible(null, 2.0, new Vec2(1000, 1000), 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> los.visible(new Vec2(0, 0), -1.0, new Vec2(10, 10), 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> los.visible(new Vec2(0, 0), 2.0, new Vec2(10, 10), Double.NaN));
    }
}