package ua.battlesim.api;

import java.util.Objects;

/**
 * Event emitted when a unit is destroyed.
 *
 * <p>Units: {@code tick} in ticks; id is a non-negative unit identifier.
 * {@code killerId} is the unit that dealt the killing blow ({@code -1} if the
 * unit died without a killer, e.g. by environmental causes).
 *
 * <p>Immutable and thread-safe; safe to deliver to the render thread.
 */
public record UnitDestroyedEvent(int tick, int victimId, int killerId, Side killerSide) implements SimEvent {

    public UnitDestroyedEvent {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0: " + tick);
        }
        if (victimId < 0) {
            throw new IllegalArgumentException("victimId must be >= 0: " + victimId);
        }
        if (killerId < -1) {
            throw new IllegalArgumentException("killerId must be >= -1: " + killerId);
        }
        Objects.requireNonNull(killerSide, "killerSide");
    }
}
