package ua.battlesim.app.internal;

import java.util.List;
import java.util.Objects;

import ua.battlesim.api.Bridge;
import ua.battlesim.api.HeightField;
import ua.battlesim.api.LineOfSight;
import ua.battlesim.api.NavGrid;
import ua.battlesim.api.PathPlanner;
import ua.battlesim.api.Prop;
import ua.battlesim.api.Road;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.WaterField;
import ua.battlesim.features.TerrainFeatures;
import ua.battlesim.math.FloatGrid;
import ua.battlesim.nav.NavBundle;
import ua.battlesim.terrain.TerrainBase;

/**
 * A fully wired, immutable {@link Terrain} (the bs-api contract) assembled by
 * {@link AppTerrainService} from the three Layer-1 producers:
 *
 * <ul>
 *   <li>bs-terrain: height, water, baked shade and sun direction
 *       ({@link TerrainBase});</li>
 *   <li>bs-features: roads, bridges, trees and rocks
 *       ({@link TerrainFeatures});</li>
 *   <li>bs-nav: the navigation grid, line-of-sight and path planner
 *       ({@link NavBundle}).</li>
 * </ul>
 *
 * <p>bs-app is the layer-4 composition root, so it is the only place that may
 * import all three at once and glue them into a single {@link Terrain}.
 *
 * <p>Units: metres for positions and sizes; radians for angles; the sun
 * direction is a unit vector in the 3-D world (x east, y up, z south).
 *
 * <p>Immutable and thread-safe after construction; the render thread only
 * reads the view methods, the simulation worker thread calls
 * {@link #nav()}, {@link #los()} and {@link #planner()}.
 */
public final class AppTerrain implements Terrain {

    private final double size;
    private final HeightField height;
    private final WaterField water;
    private final NavGrid nav;
    private final LineOfSight los;
    private final PathPlanner planner;
    private final List<Road> roads;
    private final List<Bridge> bridges;
    private final List<Prop> trees;
    private final List<Prop> rocks;
    private final double[] sunDirection;
    private final FloatGrid shade;
    private final double shadeCell;

    /**
     * @param base      the base terrain data (height, water, shade, sun) (non-null)
     * @param navBundle the navigation services (grid, line-of-sight, planner) (non-null)
     * @param features  the placed roads, bridges, trees and rocks (non-null)
     */
    public AppTerrain(TerrainBase base, NavBundle navBundle, TerrainFeatures features) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(navBundle, "navBundle");
        Objects.requireNonNull(features, "features");
        this.size = base.height().size();
        this.height = base.height();
        this.water = base.water();
        this.nav = navBundle.grid();
        this.los = navBundle.lineOfSight();
        this.planner = navBundle.planner();
        this.roads = features.roads();
        this.bridges = features.bridges();
        this.trees = features.trees();
        this.rocks = features.rocks();
        this.sunDirection = base.sunDirection().clone();
        this.shade = base.shade();
        this.shadeCell = size / shade.width();
    }

    /** @return the side length of the square map in metres */
    @Override
    public double size() {
        return size;
    }

    /** @return the ground elevation at {@code (x, z)} in metres */
    @Override
    public double heightAt(double x, double z) {
        return height.heightAt(x, z);
    }

    /** @return water depth, current and swamp over the map */
    @Override
    public WaterField water() {
        return water;
    }

    /** @return the navigation grid over the map */
    @Override
    public NavGrid nav() {
        return nav;
    }

    /** @return line-of-sight queries */
    @Override
    public LineOfSight los() {
        return los;
    }

    /** @return terrain-aware path planning */
    @Override
    public PathPlanner planner() {
        return planner;
    }

    /** @return roads on the map (immutable list, deterministic order) */
    @Override
    public List<Road> roads() {
        return roads;
    }

    /** @return bridges crossing water (immutable list, deterministic order) */
    @Override
    public List<Bridge> bridges() {
        return bridges;
    }

    /** @return placed trees (immutable list, deterministic order) */
    @Override
    public List<Prop> trees() {
        return trees;
    }

    /** @return placed rocks (immutable list, deterministic order) */
    @Override
    public List<Prop> rocks() {
        return rocks;
    }

    /** @return the unit sun direction (x east, y up, z south); a copy */
    @Override
    public double[] sunDirection() {
        return sunDirection.clone();
    }

    /** @return the terrain shading at {@code (x, z)} in {@code [0, 1]} */
    @Override
    public double shadeAt(double x, double z) {
        return shade.bilinear(x / shadeCell, z / shadeCell);
    }
}