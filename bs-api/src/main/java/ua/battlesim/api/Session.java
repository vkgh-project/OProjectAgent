package ua.battlesim.api;

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
     */
    SessionView view();
}
