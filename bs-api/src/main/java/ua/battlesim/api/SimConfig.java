package ua.battlesim.api;

/**
 * Immutable configuration of one round.
 *
 * <p>Units: {@code mapSize} in metres; {@code stepSeconds} in seconds
 * (simulated time per tick; with the default of 0.1 s, time = tick x 0.1 s);
 * {@code attackIntervalTicks}, {@code maxRoundTicks} and {@code readyTicks}
 * in ticks; {@code minUnits}/{@code maxUnits} as unit counts per side.
 *
 * <p>Immutable and thread-safe. The constructor validates all arguments.
 */
public record SimConfig(double mapSize, double stepSeconds, int attackIntervalTicks,
        int maxRoundTicks, int readyTicks, int minUnits, int maxUnits, boolean weatherEnabled) {

    public SimConfig {
        if (!Double.isFinite(mapSize) || mapSize <= 0.0) {
            throw new IllegalArgumentException("mapSize must be finite and > 0 (metres): " + mapSize);
        }
        if (!Double.isFinite(stepSeconds) || stepSeconds <= 0.0) {
            throw new IllegalArgumentException("stepSeconds must be finite and > 0: " + stepSeconds);
        }
        if (attackIntervalTicks < 1) {
            throw new IllegalArgumentException("attackIntervalTicks must be >= 1: " + attackIntervalTicks);
        }
        if (maxRoundTicks < 1) {
            throw new IllegalArgumentException("maxRoundTicks must be >= 1: " + maxRoundTicks);
        }
        if (readyTicks < 0) {
            throw new IllegalArgumentException("readyTicks must be >= 0: " + readyTicks);
        }
        if (minUnits < 1) {
            throw new IllegalArgumentException("minUnits must be >= 1: " + minUnits);
        }
        if (maxUnits < minUnits) {
            throw new IllegalArgumentException("maxUnits must be >= minUnits: " + maxUnits + " < " + minUnits);
        }
    }

    /**
     * The default configuration: 2000 m square map, 0.1 s tick, an attack
     * every 20 ticks, up to 18000 ticks per round, 20 ready ticks, 1..20
     * units per side, weather disabled.
     */
    public static SimConfig defaults() {
        return new SimConfig(2000.0, 0.1, 20, 18000, 20, 1, 20, false);
    }
}
