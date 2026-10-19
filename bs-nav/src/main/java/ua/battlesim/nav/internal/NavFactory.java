package ua.battlesim.nav.internal;

import ua.battlesim.api.HeightField;
import ua.battlesim.api.WaterField;
import ua.battlesim.nav.FeatureSet;
import ua.battlesim.nav.NavBundle;

import java.util.Objects;

/**
 * Wires the shared {@link GridModel} into the three bs-api services.
 *
 * <p>Package-internal entry point: bs-nav's public API stays in
 * {@code ua.battlesim.nav}; this class exists only so that
 * {@link ua.battlesim.nav.NavService} can compose the package-private
 * implementations.
 */
public final class NavFactory {

    private NavFactory() {
        throw new AssertionError("NavFactory is a static utility class");
    }

    public static NavBundle create(HeightField height, WaterField water, FeatureSet features) {
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(water, "water");
        Objects.requireNonNull(features, "features");
        GridModel model = new GridModel(height, water, features);
        return new NavBundle(new NavGridImpl(model), new LineOfSightImpl(model, height),
                new PathPlannerImpl(model));
    }
}
