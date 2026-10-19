package ua.battlesim.combat;

import java.util.List;
import java.util.Optional;

import ua.battlesim.api.Bridge;
import ua.battlesim.api.LineOfSight;
import ua.battlesim.api.NavGrid;
import ua.battlesim.api.PathPlanner;
import ua.battlesim.api.Prop;
import ua.battlesim.api.Road;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.Vec2;
import ua.battlesim.api.WaterField;

/**
 * Test fake of the terrain module: flat, dry, fully passable world; only the
 * line-of-sight behaviour is configurable.
 */
final class FakeTerrain implements Terrain {

    private final LineOfSight los;

    FakeTerrain(LineOfSight los) {
        this.los = los;
    }

    @Override
    public double size() {
        return 2000.0;
    }

    @Override
    public double heightAt(double x, double z) {
        return 0.0;
    }

    @Override
    public WaterField water() {
        return FAKE_WATER;
    }

    @Override
    public NavGrid nav() {
        return FAKE_NAV;
    }

    @Override
    public LineOfSight los() {
        return los;
    }

    @Override
    public PathPlanner planner() {
        return (from, to, type, radius) -> Optional.empty();
    }

    @Override
    public List<Road> roads() {
        return List.of();
    }

    @Override
    public List<Bridge> bridges() {
        return List.of();
    }

    @Override
    public List<Prop> trees() {
        return List.of();
    }

    @Override
    public List<Prop> rocks() {
        return List.of();
    }

    @Override
    public double[] sunDirection() {
        return new double[] { 0.0, 1.0, 0.0 };
    }

    @Override
    public double shadeAt(double x, double z) {
        return 1.0;
    }

    private static final WaterField FAKE_WATER = new WaterField() {
        @Override
        public double depthAt(double x, double z) {
            return 0.0;
        }

        @Override
        public Vec2 flowAt(double x, double z) {
            return new Vec2(0.0, 0.0);
        }

        @Override
        public double swampAt(double x, double z) {
            return 0.0;
        }
    };

    private static final NavGrid FAKE_NAV = new NavGrid() {
        @Override
        public int cells() {
            return 1;
        }

        @Override
        public double cellSize() {
            return 2000.0;
        }

        @Override
        public boolean passable(int i, int j) {
            return true;
        }

        @Override
        public int component(int i, int j) {
            return 0;
        }

        @Override
        public int mainComponent() {
            return 0;
        }

        @Override
        public boolean walkable(double x, double z, double radius) {
            return true;
        }

        @Override
        public boolean isRoad(double x, double z) {
            return false;
        }
    };
}