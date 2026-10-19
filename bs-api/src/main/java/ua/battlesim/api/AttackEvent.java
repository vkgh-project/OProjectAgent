package ua.battlesim.api;

/**
 * Event emitted when a unit fires a shot that was resolved by the combat
 * system.
 *
 * <p>Units: {@code tick} in ticks; {@code damage} in health points
 * ({@code [0, 100]}); ids are non-negative unit identifiers.
 *
 * <p>Immutable and thread-safe; safe to deliver to the render thread.
 */
public record AttackEvent(int tick, int attackerId, int targetId, int damage, boolean killed) implements SimEvent {

    public AttackEvent {
        if (tick < 0) {
            throw new IllegalArgumentException("tick must be >= 0: " + tick);
        }
        if (attackerId < 0) {
            throw new IllegalArgumentException("attackerId must be >= 0: " + attackerId);
        }
        if (targetId < 0) {
            throw new IllegalArgumentException("targetId must be >= 0: " + targetId);
        }
        if (attackerId == targetId) {
            throw new IllegalArgumentException("attackerId and targetId must differ: " + attackerId);
        }
        if (damage < 0) {
            throw new IllegalArgumentException("damage must be >= 0: " + damage);
        }
    }
}
