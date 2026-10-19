package ua.battlesim.api;

import java.util.List;
import java.util.Objects;

/**
 * Everything an engine needs to start one round.
 *
 * <p>Units: seed is a 64-bit deterministic seed; unit positions in metres
 * (inside {@link UnitData}); {@code terrain} in metres.
 *
 * <p>Immutable wrapper (the contained {@link UnitData} list is copied
 * defensively; the mutable units themselves remain owned by whoever created
 * the setup).
 */
public record RoundSetup(int number, long seed, Terrain terrain, List<UnitData> units, WeatherModel weather) {

    public RoundSetup {
        if (number < 1) {
            throw new IllegalArgumentException("number must be >= 1: " + number);
        }
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(units, "units");
        if (units.isEmpty()) {
            throw new IllegalArgumentException("RoundSetup must contain at least one unit");
        }
        for (UnitData u : units) {
            Objects.requireNonNull(u, "unit");
        }
        units = List.copyOf(units);
        Objects.requireNonNull(weather, "weather");
    }
}
