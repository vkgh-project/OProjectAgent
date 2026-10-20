package ua.battlesim.gfx.fx.internal;

/**
 * An immutable snapshot of one particle slot, for inspection in tests and
 * diagnostics.
 *
 * <p>Units: position/size in metres; age/life in simulated seconds; alpha and
 * colour components in {@code [0, 1]}.
 */
public record ParticleState(double x, double y, double z, double vx, double vy, double vz,
        double age, double life, double size, double alpha, double r, double g, double b) {
}
