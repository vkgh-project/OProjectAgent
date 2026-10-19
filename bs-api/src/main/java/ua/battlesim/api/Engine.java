package ua.battlesim.api;

import java.util.List;

/**
 * The deterministic simulation core, owned exclusively by the session.
 *
 * <p>Threading: all methods must be called from the single simulation worker
 * thread; the render thread may only read values it received through
 * {@link #snapshot()} and {@link #drainEvents()}.
 *
 * <p>Determinism: advancing one tick depends only on the previous state and the
 * injected systems; no wall clock, no unseeded randomness.
 */
public interface Engine {

    /** Advances the simulation by exactly one tick (one {@code stepSeconds} of simulated time). */
    void tick();

    /** Whether the round has reached an {@link #outcome() outcome}. */
    boolean finished();

    /**
     * The outcome of the round; only meaningful once {@link #finished()} is
     * {@code true}.
     */
    Outcome outcome();

    /**
     * An immutable snapshot of the current tick for the render thread.
     */
    RenderSnapshot snapshot();

    /**
     * Removes and returns the simulation events produced since the last call
     * (initially all events of the round so far).
     */
    List<SimEvent> drainEvents();

    /**
     * A deterministic hash of the full simulation state (units, weather, tick),
     * used for golden-seed tests.
     */
    long stateHash();
}
