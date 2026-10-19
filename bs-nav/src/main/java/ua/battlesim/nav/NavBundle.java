package ua.battlesim.nav;

import ua.battlesim.api.LineOfSight;
import ua.battlesim.api.NavGrid;
import ua.battlesim.api.PathPlanner;

import java.util.Objects;

/**
 * The three navigation services built by {@link NavService} for one round:
 * the discrete navigation grid, the line-of-sight query service and the
 * path planner.
 *
 * <p>All three share the same underlying grid model, so their answers are
 * mutually consistent.
 *
 * <p>Immutable record; thread-safe (all contained services are immutable).
 */
public record NavBundle(NavGrid grid, LineOfSight lineOfSight, PathPlanner planner) {

    public NavBundle {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(lineOfSight, "lineOfSight");
        Objects.requireNonNull(planner, "planner");
    }
}
