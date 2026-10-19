package ua.battlesim.features;

import ua.battlesim.api.Bridge;
import ua.battlesim.api.Prop;
import ua.battlesim.api.Road;

import java.util.List;
import java.util.Objects;

/**
 * The placed static features of a round's terrain: roads (with per-segment
 * bridge flags), the bridges that cross water, the placed trees, the placed
 * rocks and the road-corridor mask that keeps props off the roads.
 *
 * <p>Units: metres and radians throughout; x east, z south.
 *
 * <p>Determinism: two {@link TerrainFeatures} built from the same seed and
 * the same height/water fields are equal (value semantics).
 *
 * <p>Immutable record; the lists are copied defensively and thread-safe.
 */
public record TerrainFeatures(List<Road> roads, List<Bridge> bridges, List<Prop> trees,
        List<Prop> rocks, RoadCorridor roadCorridor) {

    public TerrainFeatures {
        Objects.requireNonNull(roads, "roads");
        Objects.requireNonNull(bridges, "bridges");
        Objects.requireNonNull(trees, "trees");
        Objects.requireNonNull(rocks, "rocks");
        Objects.requireNonNull(roadCorridor, "roadCorridor");
        roads = copyOf(roads);
        bridges = copyOf(bridges);
        trees = copyOf(trees);
        rocks = copyOf(rocks);
    }

    private static <T> List<T> copyOf(List<T> in) {
        for (T t : in) {
            Objects.requireNonNull(t, "element");
        }
        return List.copyOf(in);
    }
}