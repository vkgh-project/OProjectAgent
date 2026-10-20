package ua.battlesim.gfx.units.internal;

import ua.battlesim.api.RenderSnapshot;
import ua.battlesim.api.Side;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.UnitView;
import ua.battlesim.api.WeatherState;
import ua.battlesim.gfx.core.OrbitCamera;
import ua.battlesim.gfx.units.UnitRenderer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Contract tests for {@link UnitRenderer} that do not require an OpenGL
 * context: argument validation (which happens before any GPU work) and safe,
 * idempotent disposal.
 */
class UnitRendererTest {

    private static RenderSnapshot snapshot() {
        UnitView u = new UnitView(1, Side.BLUE, UnitType.TANK,
                5.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 100, UnitState.READY, 0, -1);
        WeatherState w = new WeatherState(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        return new RenderSnapshot(1, 0, 0.0, List.of(u), w);
    }

    @Test
    void constructor_requiresTerrain() {
        assertThrows(NullPointerException.class, () -> new UnitRenderer(null));
    }

    @Test
    void render_validatesArguments_beforeGpu() {
        UnitRenderer r = new UnitRenderer(new TestTerrain());
        RenderSnapshot s = snapshot();
        OrbitCamera cam = new OrbitCamera();
        assertThrows(IllegalArgumentException.class, () -> r.render(s, 1.5, cam, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> r.render(s, -0.5, cam, Optional.empty()));
        assertThrows(NullPointerException.class, () -> r.render(s, 0.5, null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> r.render(null, 0.5, cam, Optional.empty()));
        r.dispose();
    }

    @Test
    void dispose_isIdempotent() {
        UnitRenderer r = new UnitRenderer(new TestTerrain());
        assertDoesNotThrow(r::dispose);
        assertDoesNotThrow(r::dispose);
        assertDoesNotThrow(r::close);
    }
}
