package ua.battlesim.app.internal;

import java.util.Objects;

import ua.battlesim.api.SimConfig;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.TerrainService;
import ua.battlesim.features.FeaturePlacer;
import ua.battlesim.features.TerrainFeatures;
import ua.battlesim.nav.NavBundle;
import ua.battlesim.nav.NavService;
import ua.battlesim.terrain.TerrainBase;
import ua.battlesim.terrain.TerrainGenerator;

/**
 * The concrete {@link TerrainService} of the application: for a deterministic
 * seed it wires bs-terrain, bs-features and bs-nav into one fully-wired,
 * immutable {@link Terrain} ({@link AppTerrain}).
 *
 * <p>Pipeline for a seed {@code s}:
 * <ol>
 *   <li>{@link TerrainGenerator#generate(long, SimConfig)} produces the height
 *       field, water field, baked shade and sun direction;</li>
 *   <li>{@link FeaturePlacer#place(ua.battlesim.api.HeightField,
 *       ua.battlesim.api.WaterField, long)} places roads, bridges, trees and
 *       rocks from the same seed;</li>
 *   <li>{@link NavService#build(ua.battlesim.api.HeightField,
 *       ua.battlesim.api.WaterField, java.util.List, java.util.List,
 *       java.util.List)} builds the navigation grid, line-of-sight and path
 *       planner over the height, water and placed features.</li>
 * </ol>
 *
 * <p>Determinism: the same {@code (seed, config)} always yields an equivalent
 * terrain; no wall clock, no unseeded randomness.
 *
 * <p>Thread-safety: {@link #generate(long)} is a pure function of its
 * argument and the config; safe to call from the session's worker thread.
 */
public final class AppTerrainService implements TerrainService {

    private final SimConfig config;

    /**
     * @param config the round configuration (map size, etc.) (non-null)
     */
    public AppTerrainService(SimConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** @return the round configuration this service was built with */
    public SimConfig config() {
        return config;
    }

    /**
     * @param seed the 64-bit deterministic seed of the round terrain
     * @return the generated terrain (non-null)
     */
    @Override
    public Terrain generate(long seed) {
        TerrainBase base = TerrainGenerator.generate(seed, config);
        TerrainFeatures features = FeaturePlacer.place(base.height(), base.water(), seed);
        NavBundle nav = NavService.build(
                base.height(), base.water(),
                features.roads(), features.trees(), features.rocks());
        return new AppTerrain(base, nav, features);
    }
}