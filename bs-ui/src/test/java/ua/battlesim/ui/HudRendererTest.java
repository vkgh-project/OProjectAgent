package ua.battlesim.ui;

import org.junit.jupiter.api.Test;
import ua.battlesim.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link HudRenderer} — verifies HUD model generation.
 */
class HudRendererTest {

    @Test
    void render_producesValidModel() {
        HudRenderer renderer = new HudRenderer();
        SessionView view = makeView(List.of());
        HudModel model = renderer.render(view, Optional.empty());

        assertNotNull(model);
        assertNotNull(model.panelTexts());
        assertNotNull(model.markers());
        assertNotNull(model.selectedUnit());
        assertNotNull(model.banner());
        assertNotNull(model.keyHints());
    }

    @Test
    void render_hasPanelTexts() {
        HudRenderer renderer = new HudRenderer();
        SessionView view = makeView(List.of());
        HudModel model = renderer.render(view, Optional.empty());

        // Should have at least: round, time, state, speed, weather, blue, red, totals
        assertTrue(model.panelTexts().size() >= 8,
                "Expected at least 8 panel texts, got " + model.panelTexts().size());
    }

    @Test
    void render_hasKeyHints() {
        HudRenderer renderer = new HudRenderer();
        SessionView view = makeView(List.of());
        HudModel model = renderer.render(view, Optional.empty());

        assertEquals(5, model.keyHints().size());
    }

    @Test
    void render_selectedUnitPresent() {
        HudRenderer renderer = new HudRenderer();
        UnitView unit = new UnitView(42, Side.BLUE, UnitType.TANK,
                100, 200, 0, 0, 0, 0, 0, 0, 75, UnitState.READY, 0, -1);
        SessionView view = makeView(List.of(unit));
        HudModel model = renderer.render(view, Optional.of(unit));

        assertTrue(model.selectedUnit().isPresent());
        UnitPanel panel = model.selectedUnit().get();
        assertEquals(42, panel.id());
        assertEquals(75, panel.healthBar().health());
    }

    @Test
    void render_noSelectedUnit() {
        HudRenderer renderer = new HudRenderer();
        SessionView view = makeView(List.of());
        HudModel model = renderer.render(view, Optional.empty());

        assertFalse(model.selectedUnit().isPresent());
    }

    @Test
    void render_noBannerWhenRunning() {
        HudRenderer renderer = new HudRenderer();
        SessionView view = makeView(List.of());
        HudModel model = renderer.render(view, Optional.empty());

        assertFalse(model.banner().isPresent());
    }

    @Test
    void recordOutcome_tracksBlueWins() {
        HudRenderer renderer = new HudRenderer();
        renderer.recordOutcome(Outcome.BLUE_WIN);
        renderer.recordOutcome(Outcome.BLUE_WIN);
        assertEquals(2, renderer.blueWins());
        assertEquals(0, renderer.redWins());
        assertEquals(0, renderer.draws());
    }

    @Test
    void recordOutcome_tracksRedWins() {
        HudRenderer renderer = new HudRenderer();
        renderer.recordOutcome(Outcome.RED_WIN);
        assertEquals(0, renderer.blueWins());
        assertEquals(1, renderer.redWins());
    }

    @Test
    void recordOutcome_tracksDraws() {
        HudRenderer renderer = new HudRenderer();
        renderer.recordOutcome(Outcome.DRAW);
        renderer.recordOutcome(Outcome.DRAW_TIME);
        assertEquals(2, renderer.draws());
    }

    @Test
    void reset_clearsOutcomes() {
        HudRenderer renderer = new HudRenderer();
        renderer.recordOutcome(Outcome.BLUE_WIN);
        renderer.recordOutcome(Outcome.RED_WIN);
        renderer.reset();
        assertEquals(0, renderer.blueWins());
        assertEquals(0, renderer.redWins());
        assertEquals(0, renderer.draws());
    }

    @Test
    void render_nullView_throws() {
        HudRenderer renderer = new HudRenderer();
        assertThrows(NullPointerException.class,
                () -> renderer.render(null, Optional.empty()));
    }

    @Test
    void render_nullSelected_throws() {
        HudRenderer renderer = new HudRenderer();
        SessionView view = makeView(List.of());
        assertThrows(NullPointerException.class,
                () -> renderer.render(view, null));
    }

    @Test
    void recordOutcome_null_throws() {
        HudRenderer renderer = new HudRenderer();
        assertThrows(NullPointerException.class,
                () -> renderer.recordOutcome(null));
    }

    // --- Helper ---

    private static SessionView makeView(List<UnitView> units) {
        var snapshot = new RenderSnapshot(1, 100, 10.0, units,
                new WeatherState(0.5, 0.0, 0.0, 5.0, 1.0, 0.3, 0.0, 0.0, 0.0));
        int blueRem = (int) units.stream().filter(u -> u.side() == Side.BLUE).count();
        int redRem = (int) units.stream().filter(u -> u.side() == Side.RED).count();
        return new SessionView(Phase.RUNNING, 1, makeTerrain(), snapshot, 0.5,
                new Stats(blueRem, redRem), List.of(), false, 1.0);
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
