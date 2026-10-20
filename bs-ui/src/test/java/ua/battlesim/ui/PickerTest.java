package ua.battlesim.ui;

import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import ua.battlesim.api.*;
import ua.battlesim.gfx.core.OrbitCamera;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Picker} — verifies unit picking logic.
 */
class PickerTest {

    @Test
    void pick_nullView_throws() {
        OrbitCamera cam = new OrbitCamera();
        assertThrows(NullPointerException.class,
                () -> Picker.pick(100, 100, null, cam));
    }

    @Test
    void pick_nullCamera_throws() {
        SessionView view = makeView(List.of());
        assertThrows(NullPointerException.class,
                () -> Picker.pick(100, 100, view, null));
    }

    @Test
    void pick_nonFiniteCoords_throws() {
        SessionView view = makeView(List.of());
        OrbitCamera cam = new OrbitCamera();
        assertThrows(IllegalArgumentException.class,
                () -> Picker.pick(Double.NaN, 100, view, cam));
        assertThrows(IllegalArgumentException.class,
                () -> Picker.pick(100, Double.POSITIVE_INFINITY, view, cam));
    }

    @Test
    void pick_invalidViewport_throws() {
        SessionView view = makeView(List.of());
        OrbitCamera cam = new OrbitCamera();
        assertThrows(IllegalArgumentException.class,
                () -> Picker.pick(100, 100, view, cam, 0, 1080));
    }

    @Test
    void pick_noUnits_returnsEmpty() {
        SessionView view = makeView(List.of());
        OrbitCamera cam = makeCamera();
        Optional<UnitView> result = Picker.pick(960, 540, view, cam);
        assertFalse(result.isPresent());
    }

    @Test
    void pick_markerHitSelectsUnit() {
        UnitView unit = new UnitView(1, Side.BLUE, UnitType.TANK,
                0, 0, 0, 0, 0, 0, 0, 0, 100, UnitState.READY, 0, -1);
        SessionView view = makeView(List.of(unit));
        OrbitCamera cam = makeCamera();
        double[] screen = projectUnit(unit, cam, 1920, 1080);
        double mx = screen[0];
        double my = screen[1] - 24 - 16;
        Optional<UnitView> result = Picker.pick(mx, my, view, cam, 1920, 1080);
        assertTrue(result.isPresent());
        assertEquals(1, result.get().id());
    }

    @Test
    void pick_raySphereSelectsUnit() {
        UnitView unit = new UnitView(1, Side.BLUE, UnitType.TANK,
                0, 0, 0, 0, 0, 0, 0, 0, 100, UnitState.READY, 0, -1);
        SessionView view = makeView(List.of(unit));
        OrbitCamera cam = makeCamera();
        double[] screen = projectUnit(unit, cam, 1920, 1080);
        Optional<UnitView> result = Picker.pick(screen[0], screen[1], view, cam, 1920, 1080);
        assertTrue(result.isPresent());
        assertEquals(1, result.get().id());
    }

    @Test
    void pick_closestUnitWins() {
        UnitView nearUnit = new UnitView(1, Side.BLUE, UnitType.TANK,
                0, 0, 0, 0, 0, 0, 0, 0, 100, UnitState.READY, 0, -1);
        UnitView farUnit = new UnitView(2, Side.RED, UnitType.TANK,
                1000, 0, 0, 0, 0, 0, 0, 0, 100, UnitState.READY, 0, -1);
        SessionView view = makeView(List.of(farUnit, nearUnit));
        OrbitCamera cam = makeCamera();
        double[] screen = projectUnit(nearUnit, cam, 1920, 1080);
        Optional<UnitView> result = Picker.pick(screen[0], screen[1], view, cam, 1920, 1080);
        assertTrue(result.isPresent());
        assertEquals(1, result.get().id());
    }

    @Test
    void pick_deterministic() {
        UnitView u1 = new UnitView(1, Side.BLUE, UnitType.TANK,
                10, 10, 0, 0, 0, 0, 0, 0, 100, UnitState.READY, 0, -1);
        SessionView view = makeView(List.of(u1));
        OrbitCamera cam = makeCamera();
        double[] screen = projectUnit(u1, cam, 1920, 1080);
        Optional<UnitView> r1 = Picker.pick(screen[0], screen[1], view, cam, 1920, 1080);
        Optional<UnitView> r2 = Picker.pick(screen[0], screen[1], view, cam, 1920, 1080);
        assertEquals(r1, r2);
    }
    private static OrbitCamera makeCamera() {
        return new OrbitCamera(0, 0, 0, 500, 0, (float) (StrictMath.PI / 4),
                (float) (StrictMath.PI / 3), 16f / 9f, 0.1f, 10000f,
                10f, 10000f, 0.05f, (float) (StrictMath.PI - 0.05f));
    }

    private static double[] projectUnit(UnitView u, OrbitCamera cam, int vw, int vh) {
        org.joml.Matrix4f vp = cam.viewProjectionMatrix();
        Vector4f clip = new Vector4f((float) u.x(), 0.0f, (float) u.z(), 1.0f);
        vp.transform(clip);
        double ndcX = clip.x() / clip.w();
        double ndcY = clip.y() / clip.w();
        return new double[]{
                (ndcX + 1.0) / 2.0 * vw,
                (1.0 - (ndcY + 1.0) / 2.0) * vh
        };
    }

    private static SessionView makeView(List<UnitView> units) {
        var snapshot = new RenderSnapshot(1, 0, 0.0, units,
                new WeatherState(0, 0, 0, 0, 0, 0, 0, 0, 0));
        return new SessionView(Phase.RUNNING, 1, makeTerrain(), snapshot, 0.0,
                new Stats(0, 0), List.of(), false, 1.0);
    }

    private static Terrain makeTerrain() {
        return new Terrain() {
            public double size() { return 2000.0; }
            public double heightAt(double x, double z) { return 0.0; }
            public WaterField water() { return null; }
            public NavGrid nav() { return null; }
            public LineOfSight los() { return null; }
            public PathPlanner planner() { return null; }
            public List<Road> roads() { return List.of(); }
            public List<Bridge> bridges() { return List.of(); }
            public List<Prop> trees() { return List.of(); }
            public List<Prop> rocks() { return List.of(); }
            public double[] sunDirection() { return new double[]{0, 1, 0}; }
            public double shadeAt(double x, double z) { return 1.0; }
        };
    }
}
