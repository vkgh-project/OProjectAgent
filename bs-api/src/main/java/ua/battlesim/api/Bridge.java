package ua.battlesim.api;

import java.util.Objects;

import static java.lang.Double.isFinite;

/**
 * A bridge crossing water.
 *
 * <p>Units: start/end in metres (x east, z south); deckHeight as the deck
 * elevation above the water surface in metres; width in metres.
 *
 * <p>Immutable and thread-safe.
 */
public record Bridge(Vec2 start, Vec2 end, double deckHeight, double width) {

    public Bridge {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!isFinite(deckHeight) || deckHeight < 0.0) {
            throw new IllegalArgumentException("deckHeight must be finite and >= 0 (metres): " + deckHeight);
        }
        if (!isFinite(width) || width <= 0.0) {
            throw new IllegalArgumentException("width must be finite and > 0 (metres): " + width);
        }
    }
}
