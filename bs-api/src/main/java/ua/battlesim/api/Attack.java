package ua.battlesim.api;

/**
 * The result of one resolved shot.
 *
 * <p>Units: damage in health points ({@code [0, 100]}); ids are non-negative
 * unit identifiers.
 *
 * <p>Immutable and thread-safe.
 */
public record Attack(int attackerId, int targetId, int damage, boolean killed) {

    public Attack {
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
