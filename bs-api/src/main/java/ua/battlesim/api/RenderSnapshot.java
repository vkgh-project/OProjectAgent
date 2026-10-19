package ua.battlesim.api;

import java.util.List;
import java.util.Objects;

/**
 * A complete, immutable state of one simulated tick, the only simulation data
 * the render thread is allowed to read.
 *
 * <p>Units: {@code tick} in ticks; {@code timeSeconds} in seconds
 * ({@code tick x stepSeconds}); unit fields in {@link UnitView} units.
 *
 * <p>Immutable and thread-safe; the unit list is copied defensively.
 */
public record RenderSnapshot(int roundNumber, int tick, double timeSeconds,
        List<UnitView> units, WeatherState weather) {

    public RenderSnapshot {
        if (roundNumber < 1) {
            throw new IllegalArgumentException("roundNumber must be >= 1: " + roundNumber);
        }
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0: " + tick);
        }
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("timeSeconds must be finite and >= 0: " + timeSeconds);
        }
        Objects.requireNonNull(units, "units");
        for (UnitView u : units) {
            Objects.requireNonNull(u, "unit view");
        }
        units = List.copyOf(units);
        Objects.requireNonNull(weather, "weather");
    }
}
