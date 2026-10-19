package ua.battlesim.api;

import java.util.List;
import java.util.Objects;

import static java.lang.Double.isFinite;

/**
 * The complete state of a session as seen by the presentation layer: phase,
 * round, world, current snapshot, interpolation alpha, statistics, new events
 * and playback controls.
 *
 * <p>Units: {@code alpha} in {@code [0, 1]} (interpolation factor between the
 * previous and the current snapshot); {@code speed} as a multiplier of real
 * time ({@code 1.0} = 1x); tick/round numbers as named.
 *
 * <p>Immutable and thread-safe; the render thread builds this view and the UI
 * only reads it. The events list is copied defensively.
 */
public record SessionView(Phase phase, int roundNumber, Terrain terrain, RenderSnapshot snapshot,
        double alpha, Stats stats, List<SimEvent> events, boolean paused, double speed) {

    public SessionView {
        Objects.requireNonNull(phase, "phase");
        if (roundNumber < 1) {
            throw new IllegalArgumentException("roundNumber must be >= 1: " + roundNumber);
        }
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!isFinite(alpha) || alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("alpha must be in [0,1]: " + alpha);
        }
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(events, "events");
        for (SimEvent e : events) {
            Objects.requireNonNull(e, "event");
        }
        events = List.copyOf(events);
        if (!isFinite(speed) || speed < 0.0) {
            throw new IllegalArgumentException("speed must be finite and >= 0: " + speed);
        }
    }
}
