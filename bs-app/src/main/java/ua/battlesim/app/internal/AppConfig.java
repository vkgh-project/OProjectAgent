package ua.battlesim.app.internal;

import java.nio.file.Path;
import java.util.Objects;

import ua.battlesim.api.SimConfig;

/**
 * The fully resolved, immutable configuration of one application start:
 * the master seed, headless/windowed mode, the optional explicit config file
 * and the round configuration built from it.
 *
 * <p>Units: {@code simConfig} follows the {@link SimConfig} units (metres,
 * seconds, ticks, unit counts).
 *
 * <p>Immutable and thread-safe.
 *
 * @param seed       the deterministic master seed (any 64-bit value)
 * @param windowed   {@code true} for the windowed render loop
 * @param configPath the explicit {@code --config} file, or {@code null} when
 *                   the built-in defaults (or the default
 *                   {@code battle-sim.properties}) were used
 * @param simConfig  the resolved round configuration
 */
public record AppConfig(long seed, boolean windowed, Path configPath, SimConfig simConfig) {

    public AppConfig {
        Objects.requireNonNull(simConfig, "simConfig");
    }
}