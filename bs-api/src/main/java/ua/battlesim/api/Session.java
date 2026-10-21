package ua.battlesim.api;

import java.util.List;

/**
 * The session: the single owner of the engine and the only object the
 * presentation layer interacts with.
 *
 * <p>Threading: mutation methods ({@link #advance}, {@link #setPaused},
 * {@link #setSpeed}) must be called from the UI/main thread; the session
 * dispatches simulation work to its worker thread. The render thread only
 * reads {@link #view()}.
 *
 * <p>Determinism: the simulation itself is deterministic; {@code realSeconds}
 * only scales how fast ticks are produced.
 */
public interface Session {

    /**
     * Advances the session by {@code realSeconds} of wall time, producing
     * {@code floor(realSeconds / stepSeconds * speed)} simulated ticks when not
     * paused.
     *
     * @param realSeconds elapsed wall time in seconds since the last call
     */
    void advance(double realSeconds);

    /** Pauses or resumes the simulation (the render thread may still move). */
    void setPaused(boolean paused);

    /**
     * Sets the playback speed multiplier; {@code 1.0} = real time.
     *
     * @throws IllegalArgumentException if {@code speed} is not finite or negative
     */
    void setSpeed(double speed);

    /**
     * The current immutable view of the session (safe for the render thread).
     *
     * <p>Reading the view never consumes anything: pending events stay
     * pending until they are taken with {@link #drainEvents()}.
     */
    SessionView view();

    /**
     * Removes and returns all events queued so far (oldest first), together
     * with their round number and session-wide sequence. Events that the
     * presentation layer has not drained yet stay addressable through
     * {@link #view()} and are preserved across round boundaries.
     */
    List<SessionEvent> drainEvents();

    /**
     * Retries the background generation of the next round after a failed
     * attempt (safe to call at any time; a no-op while a generation is
     * already in flight).
     */
    void retryGeneration();

    /**
     * Releases the session's worker thread and stops background generation.
     * The session must not be used after close.
     */
    void close();
}
