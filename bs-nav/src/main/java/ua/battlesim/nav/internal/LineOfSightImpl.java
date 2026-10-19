package ua.battlesim.nav.internal;

import ua.battlesim.api.HeightField;
import ua.battlesim.api.LineOfSight;
import ua.battlesim.api.Vec2;

import java.util.Objects;

/**
 * bs-api {@link LineOfSight} view over the shared {@link GridModel}.
 *
 * <p>Algorithm: the sight line is sampled every 6 m between the two
 * positions, ignoring the first and last 10 m of the segment (so props and
 * terrain right at the observer/target are not counted as blockers). A
 * sample blocks the line when the ground at that point is at or above the
 * sight line height, or when a tree (10 m occluder) lies closer than
 * 10 m to the sample point.
 *
 * <p>Deterministic and read-only.
 */
final class LineOfSightImpl implements LineOfSight {

    private final GridModel model;
    private final HeightField height;

    LineOfSightImpl(GridModel model, HeightField height) {
        this.model = model;
        this.height = height;
    }

    @Override
    public boolean visible(Vec2 from, double eyeFrom, Vec2 to, double eyeTo) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!Double.isFinite(eyeFrom) || eyeFrom < 0.0) {
            throw new IllegalArgumentException("eyeFrom must be finite and >= 0 (metres): " + eyeFrom);
        }
        if (!Double.isFinite(eyeTo) || eyeTo < 0.0) {
            throw new IllegalArgumentException("eyeTo must be finite and >= 0 (metres): " + eyeTo);
        }
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        double len = StrictMath.hypot(dx, dz);
        if (len < 1e-9) {
            return true; // coincident positions, both eyes at or above their ground
        }
        double start = Math.min(GridModel.LOS_END_IGNORE, len);
        double end = Math.max(len - GridModel.LOS_END_IGNORE, start);
        if (end <= start) {
            return true; // the whole segment lies inside the ignored ends
        }
        double h0 = height.heightAt(from.x(), from.z()) + eyeFrom;
        double h1 = height.heightAt(to.x(), to.z()) + eyeTo;
        for (double s = start; s <= end + 1e-9; s += GridModel.LOS_SAMPLE_STEP) {
            double t = s / len;
            double px = from.x() + dx * t;
            double pz = from.z() + dz * t;
            double lineH = h0 + (h1 - h0) * t;
            if (height.heightAt(px, pz) >= lineH) {
                return false; // terrain rises to or above the sight line
            }
            if (model.treeGrid.within(px, pz, GridModel.TREE_OCCLUDER_RADIUS)) {
                return false; // dense forest occluder
            }
        }
        return true;
    }
}
