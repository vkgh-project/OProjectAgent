package ua.battlesim.api;

import java.util.Objects;

/**
 * A session-delivered simulation event: the {@link SimEvent} itself plus the
 * round it belongs to and its monotonically increasing session-wide sequence
 * number.
 *
 * <p>The identity (round + sequence) is assigned once, when the session
 * queues the event, and survives across round boundaries — an event produced
 * at the end of round N keeps its round-N identity even when it is drained
 * after round N+1 has started.
 *
 * <p>Units: {@code roundNumber} as a 1-based round; {@code sequence} from 1
 * upward for the whole session (the first event ever has sequence 1).
 *
 * <p>Immutable and thread-safe.
 *
 * @param roundNumber the 1-based round that produced the event
 * @param sequence    the session-wide delivery sequence, {@code >= 1}
 * @param event       the underlying simulation event (non-null)
 */
public record SessionEvent(int roundNumber, long sequence, SimEvent event) {

    public SessionEvent {
        if (roundNumber < 1) {
            throw new IllegalArgumentException("roundNumber must be >= 1: " + roundNumber);
        }
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be >= 1: " + sequence);
        }
        Objects.requireNonNull(event, "event");
    }
}