package ua.battlesim.api;

/**
 * A discrete, orderable fact about the simulation emitted by the engine and
 * consumed by the presentation layer (sound, effects, UI).
 *
 * <p>Subtypes are closed: the only permitted events are
 * {@link AttackEvent} and {@link UnitDestroyedEvent}.
 *
 * <p>Events are immutable and thread-safe; the engine produces them on the
 * simulation worker thread and the render thread only reads drained copies.
 */
public sealed interface SimEvent permits AttackEvent, UnitDestroyedEvent {
}
