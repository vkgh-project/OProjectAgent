package ua.battlesim.ui;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The complete, immutable HUD model produced by {@link HudRenderer#render}.
 *
 * <p>All positions are in screen pixel coordinates (top-left origin).
 *
 * <p>Immutable and thread-safe; all lists are copied defensively.
 */
public record HudModel(
        List<HudText> panelTexts,
        List<NatoMarker> markers,
        Optional<UnitPanel> selectedUnit,
        Optional<Banner> banner,
        List<KeyHint> keyHints
) {
    public HudModel {
        Objects.requireNonNull(panelTexts, "panelTexts");
        for (var t : panelTexts) {
            Objects.requireNonNull(t, "panelText");
        }
        panelTexts = List.copyOf(panelTexts);
        Objects.requireNonNull(markers, "markers");
        for (var m : markers) {
            Objects.requireNonNull(m, "marker");
        }
        markers = List.copyOf(markers);
        Objects.requireNonNull(selectedUnit, "selectedUnit");
        Objects.requireNonNull(banner, "banner");
        Objects.requireNonNull(keyHints, "keyHints");
        for (var k : keyHints) {
            Objects.requireNonNull(k, "keyHint");
        }
        keyHints = List.copyOf(keyHints);
    }
}
