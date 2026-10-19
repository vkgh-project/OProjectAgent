package ua.battlesim.nav;

import ua.battlesim.api.Prop;
import ua.battlesim.api.Road;

import java.util.List;
import java.util.Objects;

/**
 * The placed static features that influence navigation: roads (with their
 * width), the placed trees (each one blocks the cell it stands in and acts
 * as a line-of-sight occluder) and the placed rocks (a rock with
 * {@code scale >= 1.4} blocks the cell it stands in).
 *
 * <p>bs-nav is a Layer-1 module and may not depend on bs-features
 * (same layer); it therefore receives the navigation-relevant feature data
 * as the bs-api value types ({@link Road}, {@link Prop}) instead of the
 * bs-features {@code TerrainFeatures} record. The composition root
 * (bs-app) extracts {@code roads()}, {@code trees()} and {@code rocks()}
 * from the features and passes them here.
 *
 * <p>Units: metres and radians; x east, z south.
 *
 * <p>Immutable record; the lists are copied defensively and thread-safe.
 */
public record FeatureSet(List<Road> roads, List<Prop> trees, List<Prop> rocks) {

    public FeatureSet {
        roads = copyValidated(roads, "roads");
        trees = copyValidated(trees, "trees");
        rocks = copyValidated(rocks, "rocks");
    }

    /** @return a feature set with no roads, trees or rocks */
    public static FeatureSet empty() {
        return new FeatureSet(List.of(), List.of(), List.of());
    }

    private static <T> List<T> copyValidated(List<T> in, String name) {
        Objects.requireNonNull(in, name);
        for (T t : in) {
            Objects.requireNonNull(t, name + " element");
        }
        return List.copyOf(in);
    }
}
