package ua.battlesim.ui;

import ua.battlesim.api.Side;
import ua.battlesim.api.UnitType;

/**
 * A NATO symbol marker positioned above a unit in screen space.
 *
 * <p>The marker is drawn at a constant pixel size regardless of camera distance.
 * Destroyed units are rendered dimmed.
 *
 * <p>Immutable and thread-safe.
 */
public record NatoMarker(int unitId, Side side, UnitType type, Rect rect, boolean destroyed) {

    public NatoMarker {
        if (unitId < 0) {
            throw new IllegalArgumentException("unitId must be >= 0: " + unitId);
        }
        if (side == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (rect == null) {
            throw new IllegalArgumentException("rect must not be null");
        }
    }
}
