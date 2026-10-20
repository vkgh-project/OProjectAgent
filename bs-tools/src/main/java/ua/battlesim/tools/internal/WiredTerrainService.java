package ua.battlesim.tools.internal;

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
 * The concrete {@link TerrainService} for the headless pipeline: for a
 * deterministic seed it wires bs-terrain, bs-features and bs-nav into one
 * fully-wired, immutable {@link Terrain} (see {@link WiredTerrain}).
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
 * terrain; only the deterministic bs-math / core modules are consulted — no
 * wall clock, no unseeded randomness.
 *
 * <p>Thread-safety: {@link #generate(long)} is a pure function of its
 * argument and the injected config; safe to call from the single worker thread
 * that generates a round.
 */
public final class WiredTerrainService implements TerrainService {

    private final SimConfig config;

    /**
     * @param config the round configuration (map size, etc.) (non-null)
     */
    public WiredTerrainService(SimConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    /** @return the round configuration this service was built with */
    public SimConfig config() {
        return config;
    }

    @Override
    public Terrain generate(long seed) {
        TerrainBase base = TerrainGenerator.generate(seed, config);
        TerrainFeatures features = FeaturePlacer.place(base.height(), base.water(), seed);
        NavBundle nav = NavService.build(
                base.height(), base.water(),
                features.roads(), features.trees(), features.rocks());
        return new WiredTerrain(base, nav, features);
    }
}
