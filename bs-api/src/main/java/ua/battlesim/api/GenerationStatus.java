package ua.battlesim.api;

/**
 * The immutable state of the session's background round generation, as seen
 * by the presentation layer.
 *
 * <p>The session always keeps the round after {@code nextRound} one step
 * ahead: {@code pending} is true while that generation is in flight on the
 * worker thread; {@code failure} describes the last failed generation
 * attempt (or {@code null} when the latest attempt has not failed / no
 * attempt has failed).
 *
 * <p>Immutable and thread-safe.
 *
 * @param nextRound the round currently being generated or awaited,
 *                  {@code >= 1}
 * @param pending   whether a generation attempt is in flight
 * @param failure   the last generation failure description, or {@code null}
 */
public record GenerationStatus(int nextRound, boolean pending, String failure) {

    public GenerationStatus {
        if (nextRound < 1) {
            throw new IllegalArgumentException("nextRound must be >= 1: " + nextRound);
        }
    }
}