package ua.battlesim.nav;

import ua.battlesim.api.HeightField;
import ua.battlesim.api.Prop;
import ua.battlesim.api.Road;
import ua.battlesim.api.WaterField;
import ua.battlesim.nav.internal.NavFactory;

import java.util.List;
import java.util.Objects;

/**
 * Builds the navigation services ({@link ua.battlesim.api.NavGrid},
 * {@link ua.battlesim.api.LineOfSight}, {@link ua.battlesim.api.PathPlanner})
 * for one round from the round's height field, water field and placed
 * features.
 *
 * <p>The module is Layer 1 and may only depend on bs-api and bs-math; the
 * feature data therefore arrives as the bs-api value types
 * ({@link Road}, {@link Prop}) collected into a {@link FeatureSet}. The
 * composition root (bs-app) passes the {@code roads()}, {@code trees()}
 * and {@code rocks()} of the round's terrain features.
 *
 * <p>Units: metres (x east, z south), metres per metre for slopes.
 *
 * <p>Deterministic: the same inputs always produce equivalent services;
 * no wall clock, no unseeded randomness, StrictMath for trigonometry.
 *
 * <p>The returned {@link NavBundle} is immutable and thread-safe.
 */
public final class NavService {

    private NavService() {
        throw new AssertionError("NavService is a static utility class");
    }

    /**
     * Builds the navigation services for terrain without placed features.
     *
     * @param height the round height field (non-null, finite size &gt; 0)
     * @param water  the round water field (non-null, may be dry)
     * @return the navigation bundle
     * @throws IllegalArgumentException if the map size is not finite and &gt; 0
     */
    public static NavBundle build(HeightField height, WaterField water) {
        return build(height, water, FeatureSet.empty());
    }

    /**
     * Builds the navigation services from roads, trees and rocks given
     * directly as bs-api value types.
     *
     * @param height the round height field (non-null, finite size &gt; 0)
     * @param water  the round water field (non-null, may be dry)
     * @param roads  the placed roads (non-null, may be empty)
     * @param trees  the placed trees (non-null, may be empty)
     * @param rocks  the placed rocks (non-null, may be empty)
     * @return the navigation bundle
     * @throws IllegalArgumentException on an invalid map size or list
     */
    public static NavBundle build(HeightField height, WaterField water,
                                  List<Road> roads, List<Prop> trees, List<Prop> rocks) {
        return build(height, water, new FeatureSet(roads, trees, rocks));
    }

    /**
     * Builds the navigation services for the round's terrain and features.
     *
     * @param height   the round height field (non-null, finite size &gt; 0)
     * @param water    the round water field (non-null, may be dry)
     * @param features the placed roads, trees and rocks (non-null)
     * @return the navigation bundle (grid, line-of-sight, path planner)
     * @throws IllegalArgumentException on an invalid map size or feature list
     */
    public static NavBundle build(HeightField height, WaterField water, FeatureSet features) {
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(water, "water");
        Objects.requireNonNull(features, "features");
        return NavFactory.create(height, water, features);
    }
}
