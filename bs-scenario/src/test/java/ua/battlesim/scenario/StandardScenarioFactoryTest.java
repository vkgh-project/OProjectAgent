package ua.battlesim.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ua.battlesim.api.Side.BLUE;
import static ua.battlesim.api.Side.RED;

import ua.battlesim.api.NavGrid;
import ua.battlesim.api.RoundSetup;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.TerrainService;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitInfo;
import ua.battlesim.api.UnitState;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Unit tests of {@link StandardScenarioFactory} against the fake terrain
 * service ({@link FakeTerrainService}); no real terrain, nav or weather
 * module is involved (fakes of neighbouring modules per the testing rules).
 */
final class StandardScenarioFactoryTest {

    private static final double EPS = 1e-9;

    private static StandardScenarioFactory factory(SimConfig cfg, TerrainService terrain) {
        return new StandardScenarioFactory(terrain, cfg);
    }

    @Test
    void sameSeedAndRoundGiveIdenticalSetup() {
        RoundSetup a = factory(SimConfig.defaults(), new FakeTerrainService()).create(12345L, 3);
        RoundSetup b = factory(SimConfig.defaults(), new FakeTerrainService()).create(12345L, 3);
        assertEquals(3, a.number());
        assertEquals(a.seed(), b.seed());
        assertEquals(a.units().size(), b.units().size());
        for (int i = 0; i < a.units().size(); i++) {
            assertUnitEqual(a.units().get(i), b.units().get(i));
        }
    }

    @Test
    void differentRoundsDiffer() {
        StandardScenarioFactory f = factory(SimConfig.defaults(), new FakeTerrainService());
        RoundSetup r1 = f.create(777L, 1);
        RoundSetup r2 = f.create(777L, 2);
        assertFalse(r1.seed() == r2.seed() && sameUnits(r1.units(), r2.units()),
                "rounds 1 and 2 of master seed 777 must not be identical");
    }

    @Test
    void unitCountsStayWithinConfigRange() {
        for (long seed = 0; seed < 25; seed++) {
            RoundSetup setup = factory(SimConfig.defaults(), new FakeTerrainService())
                    .create(1000 + seed, 1);
            long blue = countOf(setup, BLUE);
            long red = countOf(setup, RED);
            assertTrue(blue >= 1 && blue <= 20, "BLUE count " + blue + " out of [1, 20]");
            assertTrue(red >= 1 && red <= 20, "RED count " + red + " out of [1, 20]");
        }
    }

    @Test
    void customUnitRangeIsRespected() {
        SimConfig cfg = new SimConfig(2000.0, 0.1, 20, 18000, 20, 3, 5, false);
        for (long seed = 0; seed < 10; seed++) {
            RoundSetup setup = factory(cfg, new FakeTerrainService()).create(5000 + seed, 1);
            long blue = countOf(setup, BLUE);
            long red = countOf(setup, RED);
            assertTrue(blue >= 3 && blue <= 5, "BLUE count " + blue + " out of [3, 5]");
            assertTrue(red >= 3 && red <= 5, "RED count " + red + " out of [3, 5]");
        }
    }

    @Test
    void spawnBandsMarginsAndNavRulesHold() {
        RoundSetup setup = factory(SimConfig.defaults(), new FakeTerrainService()).create(42L, 1);
        double size = setup.terrain().size();
        NavGrid nav = setup.terrain().nav();
        for (UnitData u : setup.units()) {
            if (u.side() == BLUE) {
                assertTrue(u.z() <= size * 0.35 + EPS,
                        "BLUE at z=" + u.z() + " outside the northern 35% of " + size);
            } else {
                assertTrue(u.z() >= size * 0.65 - EPS,
                        "RED at z=" + u.z() + " outside the southern 35% of " + size);
            }
            assertTrue(u.x() >= 120.0 - EPS && u.x() <= size - 120.0 + EPS,
                    "unit " + u.id() + " at x=" + u.x() + " violates the 120 m side margin");
            int i = (int) StrictMath.floor(u.x() / nav.cellSize());
            int j = (int) StrictMath.floor(u.z() / nav.cellSize());
            assertTrue(nav.passable(i, j), "unit " + u.id() + " on an impassable cell");
            assertEquals(nav.mainComponent(), nav.component(i, j),
                    "unit " + u.id() + " outside the main nav component");
            assertTrue(nav.walkable(u.x(), u.z(), UnitInfo.of(u.type()).radius() + 1.0),
                    "unit " + u.id() + " footprint plus 1 m does not fit on passable cells");
        }
        List<UnitData> units = setup.units();
        for (int i = 0; i < units.size(); i++) {
            for (int k = i + 1; k < units.size(); k++) {
                double dx = units.get(i).x() - units.get(k).x();
                double dz = units.get(i).z() - units.get(k).z();
                double need = 4.0 + UnitInfo.of(units.get(i).type()).radius()
                        + UnitInfo.of(units.get(k).type()).radius();
                assertTrue(dx * dx + dz * dz >= need * need - 1e-6,
                        "units " + units.get(i).id() + " and " + units.get(k).id()
                                + " closer than " + need + " m");
            }
        }
    }

    @Test
    void unitsStartReadyAtFullHealthFacingTheEnemy() {
        RoundSetup setup = factory(SimConfig.defaults(), new FakeTerrainService()).create(9L, 1);
        for (UnitData u : setup.units()) {
            assertEquals(UnitState.READY, u.state(), "unit " + u.id() + " state");
            assertEquals(100, u.health(), "unit " + u.id() + " health");
            assertEquals(0, u.cooldown(), "unit " + u.id() + " cooldown");
            assertEquals(0.0, u.odometer(), 0.0, "unit " + u.id() + " odometer");
            assertEquals(-1, u.destroyedTick(), "unit " + u.id() + " destroyedTick");
            double expected = u.side() == BLUE ? StrictMath.PI / 2.0 : -StrictMath.PI / 2.0;
            assertEquals(expected, u.heading(), 1e-12,
                    "unit " + u.id() + " heading toward the enemy side");
            assertEquals(u.heading(), u.aim(), 1e-12, "unit " + u.id() + " aim equals heading");
        }
    }

    @Test
    void unitIdsAreUniqueAndAscending() {
        RoundSetup setup = factory(SimConfig.defaults(), new FakeTerrainService()).create(21L, 1);
        List<UnitData> units = setup.units();
        Set<Integer> ids = new HashSet<>();
        for (UnitData u : units) {
            assertTrue(ids.add(u.id()), "duplicate unit id " + u.id());
        }
        int expected = 0;
        for (UnitData u : units) {
            assertEquals(expected++, u.id(), "ids must be 0..n-1 in list order");
        }
    }

    @Test
    void twoHundredSeedsNeverFail() {
        TerrainService terrain = new FakeTerrainService();
        for (long seed = 1; seed <= 200; seed++) {
            RoundSetup setup = factory(SimConfig.defaults(), terrain)
                    .create(seed * 7919L, (int) (seed % 3) + 1);
            assertTrue(setup.units().size() >= 2, "seed " + seed + " produced fewer than 2 units");
            assertTrue(countOf(setup, BLUE) >= 1 && countOf(setup, RED) >= 1,
                    "seed " + seed + " is missing a side");
            for (UnitData u : setup.units()) {
                assertTrue(u.x() > 0.0 && u.x() < setup.terrain().size(),
                        "seed " + seed + ": unit " + u.id() + " x out of map");
                assertTrue(u.z() >= 0.0 && u.z() <= setup.terrain().size(),
                        "seed " + seed + ": unit " + u.id() + " z out of map");
            }
        }
    }

    @Test
    void weatherIsSeededFromTheRoundSeed() {
        SimConfig cfg = new SimConfig(2000.0, 0.1, 20, 18000, 20, 1, 20, true);
        StandardScenarioFactory f = factory(cfg, new FakeTerrainService());
        RoundSetup a = f.create(555L, 2);
        RoundSetup b = f.create(555L, 2);
        RoundSetup c = f.create(556L, 2);
        boolean differsFromOtherMaster = false;
        for (int tick = 0; tick < 30; tick++) {
            a.weather().step(tick);
            b.weather().step(tick);
            c.weather().step(tick);
            assertEquals(a.weather().state(), b.weather().state(),
                    "same master seed and round must give the same weather at tick " + tick);
            differsFromOtherMaster |= !a.weather().state().equals(c.weather().state());
        }
        assertTrue(differsFromOtherMaster,
                "different master seeds must give different weather");
    }

    @Test
    void disabledWeatherIsPermanentlyClear() {
        SimConfig cfg = new SimConfig(2000.0, 0.1, 20, 18000, 20, 1, 20, false);
        RoundSetup setup = factory(cfg, new FakeTerrainService()).create(31337L, 1);
        for (int tick = 0; tick < 10; tick++) {
            setup.weather().step(tick);
            assertEquals(0.0, setup.weather().state().rain(), 0.0);
            assertEquals(0.0, setup.weather().state().heavyRain(), 0.0);
            assertEquals(0.0, setup.weather().state().snow(), 0.0);
            assertEquals(1.0, setup.weather().speedFactor(1.0, 0.0), 0.0);
            assertEquals(1.0, setup.weather().visibility(), 0.0);
            assertEquals(1.0, setup.weather().combatFactor(), 0.0);
        }
    }

    @Test
    void constructorAndCreateValidateArguments() {
        assertThrows(NullPointerException.class,
                () -> new StandardScenarioFactory(null, SimConfig.defaults()));
        assertThrows(NullPointerException.class,
                () -> new StandardScenarioFactory(new FakeTerrainService(), null));
        StandardScenarioFactory f = factory(SimConfig.defaults(), new FakeTerrainService());
        assertThrows(IllegalArgumentException.class, () -> f.create(1L, 0));
        assertThrows(IllegalArgumentException.class, () -> f.create(1L, -3));
    }

    private static long countOf(RoundSetup setup, ua.battlesim.api.Side side) {
        long count = 0;
        for (UnitData u : setup.units()) {
            if (u.side() == side) {
                count++;
            }
        }
        return count;
    }

    private static void assertUnitEqual(UnitData a, UnitData b) {
        assertEquals(a.id(), b.id(), "id");
        assertEquals(a.side(), b.side(), "side");
        assertEquals(a.type(), b.type(), "type");
        assertEquals(a.x(), b.x(), 0.0, "x");
        assertEquals(a.z(), b.z(), 0.0, "z");
        assertEquals(a.heading(), b.heading(), 0.0, "heading");
        assertEquals(a.aim(), b.aim(), 0.0, "aim");
        assertEquals(a.health(), b.health(), "health");
        assertEquals(a.state(), b.state(), "state");
        assertEquals(a.cooldown(), b.cooldown(), "cooldown");
        assertEquals(a.odometer(), b.odometer(), 0.0, "odometer");
        assertEquals(a.destroyedTick(), b.destroyedTick(), "destroyedTick");
    }

    private static boolean sameUnits(List<UnitData> a, List<UnitData> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            UnitData x = a.get(i);
            UnitData y = b.get(i);
            if (x.id() != y.id() || x.side() != y.side() || x.type() != y.type()
                    || x.x() != y.x() || x.z() != y.z()) {
                return false;
            }
        }
        return true;
    }
}
