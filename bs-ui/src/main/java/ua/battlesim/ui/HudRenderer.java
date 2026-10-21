package ua.battlesim.ui;

import ua.battlesim.api.*;
import ua.battlesim.ui.internal.CompassHeading;
import ua.battlesim.ui.internal.LayoutMath;
import ua.battlesim.ui.internal.ScreenProjection;

import java.util.*;

/**
 * Renders the HUD (heads-up display) for the battle simulation.
 *
 * <p>Produces a {@link HudModel} containing all text elements, NATO markers,
 * the selected unit panel, the round-result banner, and key hints.
 *
 * <p>Strings are loaded from a Ukrainian ResourceBundle.
 *
 * <p>Deterministic: no wall clock inside the simulation; uses only data from
 * the {@link SessionView}.
 *
 * <p>Thread-safety: not thread-safe; use from a single thread.
 */
public final class HudRenderer {

    private static final ResourceBundle BUNDLE =
            ResourceBundle.getBundle("ua.battlesim.ui.messages", Locale.of("uk"));

    /** Default viewport width in pixels. */
    public static final int DEFAULT_VIEWPORT_WIDTH = 1920;

    /** Default viewport height in pixels. */
    public static final int DEFAULT_VIEWPORT_HEIGHT = 1080;

    /** ARGB color for primary text (white). */
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    /** ARGB color for blue side (blue). */
    private static final int COLOR_BLUE = 0xFF4488FF;

    /** ARGB color for red side (red). */
    private static final int COLOR_RED = 0xFFFF4444;

    /** ARGB color for dimmed/destroyed (gray). */
    private static final int COLOR_DIM = 0xFF888888;

    /** ARGB color for key hints (light gray). */
    private static final int COLOR_HINT = 0xFFCCCCCC;

    // Tracked round outcomes
    private int blueWins = 0;
    private int redWins = 0;
    private int draws = 0;

    /**
     * Records a round outcome for the totals display.
     *
     * @param outcome the round result (non-null)
     * @throws NullPointerException if outcome is null
     */
    public void recordOutcome(Outcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        switch (outcome) {
            case BLUE_WIN -> blueWins++;
            case RED_WIN -> redWins++;
            case DRAW, DRAW_TIME -> draws++;
        }
    }

    /** Resets all tracked round outcomes to zero. */
    public void reset() {
        blueWins = 0;
        redWins = 0;
        draws = 0;
    }

    /** @return the number of BLUE wins tracked so far */
    public int blueWins() { return blueWins; }

    /** @return the number of RED wins tracked so far */
    public int redWins() { return redWins; }

    /** @return the number of draws tracked so far */
    public int draws() { return draws; }

    /**
     * Renders the HUD for the given session view.
     *
     * @param view the current session view (non-null)
     * @param selected the selected unit (empty if none, non-null)
     * @return the complete HUD model
     * @throws NullPointerException if view or selected is null
     */
    public HudModel render(SessionView view, Optional<UnitView> selected) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(selected, "selected");
        return renderInternal(view, selected, null, DEFAULT_VIEWPORT_WIDTH, DEFAULT_VIEWPORT_HEIGHT);
    }

    /**
     * Renders the HUD with a camera for NATO marker projection.
     *
     * @param view the current session view (non-null)
     * @param selected the selected unit (empty if none, non-null)
     * @param camera the orbit camera (may be null)
     * @param viewportWidth viewport width in pixels, &gt; 0
     * @param viewportHeight viewport height in pixels, &gt; 0
     * @return the complete HUD model
     */
    public HudModel render(SessionView view, Optional<UnitView> selected,
                           ua.battlesim.gfx.core.OrbitCamera camera,
                           int viewportWidth, int viewportHeight) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(selected, "selected");
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            throw new IllegalArgumentException("viewport must be > 0: " + viewportWidth + ", " + viewportHeight);
        }
        return renderInternal(view, selected, camera, viewportWidth, viewportHeight);
    }

    private HudModel renderInternal(SessionView view, Optional<UnitView> selected,
                                    ua.battlesim.gfx.core.OrbitCamera camera,
                                    int vw, int vh) {
        List<HudText> texts = new ArrayList<>();
        List<NatoMarker> markers = new ArrayList<>();
        List<KeyHint> hints = new ArrayList<>();
        Optional<Banner> banner = Optional.empty();

        RenderSnapshot snapshot = view.snapshot();
        WeatherState weather = snapshot.weather();
        Stats stats = view.stats();
        List<UnitView> units = snapshot.units();

        int totalBlue = 0, totalRed = 0;
        for (UnitView u : units) {
            if (u.side() == Side.BLUE) totalBlue++;
            else totalRed++;
        }
        int blueDestroyed = totalBlue - stats.blueRemaining();
        int redDestroyed = totalRed - stats.redRemaining();

        int line = 0;
        texts.add(new HudText(BUNDLE.getString("round") + " " + view.roundNumber(),
                LayoutMath.panelTextLineRect(line++), LayoutMath.FONT_SIZE, COLOR_WHITE));
        String timeStr = LayoutMath.formatTime(snapshot.timeSeconds()) + " / " + LayoutMath.formatTime(LayoutMath.MAX_TIME_SECONDS);
        texts.add(new HudText(timeStr, LayoutMath.panelTextLineRect(line++), LayoutMath.FONT_SIZE, COLOR_WHITE));
        String stateStr = BUNDLE.getString(stateKey(view.phase(), view.paused()));
        texts.add(new HudText(stateStr, LayoutMath.panelTextLineRect(line++), LayoutMath.FONT_SIZE, COLOR_WHITE));
        String speedStr = BUNDLE.getString("speed") + ": " + formatSpeed(view.speed());
        texts.add(new HudText(speedStr, LayoutMath.panelTextLineRect(line++), LayoutMath.FONT_SIZE, COLOR_WHITE));
        line++;
        String weatherStr = BUNDLE.getString("rain") + ": " + pct(weather.rain())
                + ", " + BUNDLE.getString("snow") + ": " + pct(weather.snow())
                + ", " + BUNDLE.getString("wind") + ": " + (int) weather.windSpeed() + " m/s"
                + ", " + BUNDLE.getString("cloud") + ": " + pct(weather.cloud());
        texts.add(new HudText(weatherStr, LayoutMath.panelTextLineRect(line++), LayoutMath.FONT_SIZE, COLOR_WHITE));
        String blueStr = BUNDLE.getString("blue") + ": " + stats.blueRemaining() + "/" + blueDestroyed;
        texts.add(new HudText(blueStr, LayoutMath.panelTextLineRect(line++), LayoutMath.FONT_SIZE, COLOR_BLUE));
        String redStr = BUNDLE.getString("red") + ": " + stats.redRemaining() + "/" + redDestroyed;
        texts.add(new HudText(redStr, LayoutMath.panelTextLineRect(line++), LayoutMath.FONT_SIZE, COLOR_RED));
        String totalsStr = BUNDLE.getString("rounds") + ": " + view.roundNumber()
                + ", " + BUNDLE.getString("blue_wins") + ": " + blueWins
                + ", " + BUNDLE.getString("red_wins") + ": " + redWins
                + ", " + BUNDLE.getString("draws") + ": " + draws;
        texts.add(new HudText(totalsStr, LayoutMath.panelTextLineRect(line++), LayoutMath.FONT_SIZE, COLOR_WHITE));

        // NATO markers (if camera provided)
        if (camera != null) {
            for (UnitView u : units) {
                double[] screen = ScreenProjection.project(u.x(), 0.0, u.z(),
                        camera.viewProjectionMatrix(), vw, vh);
                Rect markerRect = LayoutMath.markerRect(screen[0], screen[1]);
                boolean destroyed = u.state() == UnitState.DESTROYED;
                markers.add(new NatoMarker(u.id(), u.side(), u.type(), markerRect, destroyed));
            }
        }

        // Selected unit panel
        Optional<UnitPanel> unitPanel = selected.map(u -> {
            String sideLbl = u.side() == Side.BLUE ? BUNDLE.getString("blue") : BUNDLE.getString("red");
            String typeLbl = BUNDLE.getString(typeKey(u.type()));
            String coords = String.format("%.1f, %.1f", u.x(), u.z());
            String compass = CompassHeading.toCompass(u.heading());
            HealthBar hb = new HealthBar(
                    new Rect(LayoutMath.PANEL_WIDTH + 20, 200, LayoutMath.UNIT_PANEL_WIDTH - 40, 16),
                    u.health(), 100);
            String stateLbl = BUNDLE.getString(stateUnitKey(u.state()));
            return new UnitPanel(u.id(), sideLbl, typeLbl, coords, compass, hb, stateLbl, "",
                    LayoutMath.unitPanelRect(vh));
        });

        // Banner (if finished)
        if (view.phase() == Phase.FINISHED) {
            banner = Optional.of(new Banner(BUNDLE.getString("state_finished"),
                    LayoutMath.bannerRect(vw), Outcome.DRAW));
        }

        // Key hints
        int hintX = 10;
        String[][] hintDefs = {
                {"SPACE", BUNDLE.getString("hint_pause")},
                {"+", BUNDLE.getString("hint_speed_up")},
                {"-", BUNDLE.getString("hint_speed_down")},
                {"R", BUNDLE.getString("hint_camera")},
                {"F1", BUNDLE.getString("hint_focus")}
        };
        for (String[] hd : hintDefs) {
            hints.add(new KeyHint(hd[0], hd[1], new Rect(hintX, vh - 25, 100, 20)));
            hintX += 120;
        }

        return new HudModel(texts, markers, unitPanel, banner, hints);
    }

    private static String stateKey(Phase phase, boolean paused) {
        return switch (phase) {
            case RUNNING -> paused ? "state_paused" : "state_running";
            case INTERMISSION, GENERATING -> "state_generating";
            case FINISHED -> "state_finished";
        };
    }

    private static String stateUnitKey(UnitState state) {
        return switch (state) {
            case READY -> "state_ready";
            case MOVING -> "state_moving";
            case ATTACKING -> "state_attacking";
            case WAITING -> "state_waiting";
            case DESTROYED -> "state_destroyed";
        };
    }

    private static String typeKey(UnitType type) {
        return switch (type) {
            case TANK -> "type_tank";
            case INFANTRY -> "type_infantry";
            case APC_INFANTRY -> "type_apc";
        };
    }

    private static String pct(double v) {
        return (int) (v * 100) + "%";
    }

    private static String formatSpeed(double speed) {
        if (speed == Math.floor(speed)) {
            return (int) speed + "x";
        }
        return speed + "x";
    }
}
