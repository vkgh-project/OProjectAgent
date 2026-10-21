package ua.battlesim.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.AttackEvent;
import ua.battlesim.api.Engine;
import ua.battlesim.api.EngineFactory;
import ua.battlesim.api.Outcome;
import ua.battlesim.api.Phase;
import ua.battlesim.api.RenderSnapshot;
import ua.battlesim.api.RoundSetup;
import ua.battlesim.api.ScenarioFactory;
import ua.battlesim.api.Session;
import ua.battlesim.api.SessionEvent;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.SimEvent;
import ua.battlesim.api.Side;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.UnitView;
import ua.battlesim.api.WeatherModel;
import ua.battlesim.api.WeatherState;

/**
 * Event semantics of the session: {@code view()} is read-only (it never
 * consumes events), {@code drainEvents()} consumes them exactly once, events
 * keep their round and sequence identity across round boundaries, and a
 * failed background generation is surfaced and can be retried.
 */
final class SessionEventsTest {

    private static final long TIMEOUT_MS = 5000;
    private static final WeatherModel NO_WEATHER = new WeatherModel() {
        public void step(int tick) {
        }

        public WeatherState state() {
            return new WeatherState(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public double speedFactor(double dirX, double dirZ) {
            return 1.0;
        }

        public double visibilityMetres() {
            return 1000.0;
        }

        public double combatFactor() {
            return 1.0;
        }
    };

    private static final List<UnitView> UNITS = List.of(
            new UnitView(0, Side.BLUE, UnitType.TANK, 100, 100, 100, 100, 0, 0, 0, 0, 100,
                    UnitState.READY, 0.0, -1),
            new UnitView(1, Side.RED, UnitType.TANK, 1900, 1900, 1900, 1900, 0, 0, 0, 0, 100,
                    UnitState.READY, 0.0, -1));

    /** Emits one attack event on the last tick before it finishes. */
    private static final class EventEngine implements Engine {

        private final int round;
        private final int finishAfter;
        private int tick;

        private EventEngine(int round, int finishAfter) {
            this.round = round;
            this.finishAfter = finishAfter;
        }

        @Override
        public void tick() {
            tick++;
        }

        @Override
        public boolean finished() {
            return tick >= finishAfter;
        }

        @Override
        public Outcome outcome() {
            return Outcome.BLUE_WIN;
        }

        @Override
        public RenderSnapshot snapshot() {
            return new RenderSnapshot(round, tick, tick * 0.1, UNITS, NO_WEATHER.state());
        }

        @Override
        public List<SimEvent> drainEvents() {
            if (tick == finishAfter) {
                return List.of(new AttackEvent(tick, 0, 1, 10, false));
            }
            return List.of();
        }

        @Override
        public long stateHash() {
            return round * 1_000_003L + tick;
        }
    }

    private static EngineFactory eventEngines(int finishAfter) {
        return (setup, cfg) -> new EventEngine(setup.number(), finishAfter);
    }

    private static ScenarioFactory scenarios() {
        return (masterSeed, roundNumber) -> new RoundSetup(roundNumber, masterSeed,
                FakeTerrain.standard(), List.of(
                        new ua.battlesim.api.UnitData(0, Side.BLUE, UnitType.TANK, 100, 100, 0, 0,
                                100, UnitState.READY, 0, 0.0, -1),
                        new ua.battlesim.api.UnitData(1, Side.RED, UnitType.TANK, 1900, 1900, 0, 0,
                                100, UnitState.READY, 0, 0.0, -1)),
                NO_WEATHER);
    }

    @Test
    void viewIsReadOnlyAndDrainConsumesExactlyOnce() {
        SimConfig cfg = SimConfig.defaults();
        Session s = new DefaultSession(scenarios(), eventEngines(5), cfg, 42L);
        s.advance(0.5);   // 5 ticks -> the engine emits one event

        SessionEvent ev = s.view().events().get(0);
        assertEquals(1, ev.roundNumber());
        assertEquals(1, ev.sequence());

        // Re-reading the view must not consume the event.
        assertEquals(1, s.view().events().size(), "view() must not consume events");

        List<SessionEvent> drained = s.drainEvents();
        assertEquals(1, drained.size());
        assertEquals(ev, drained.get(0));
        assertTrue(s.view().events().isEmpty(), "drainEvents() must empty the queue");
        assertTrue(s.drainEvents().isEmpty(), "draining twice must not repeat the event");
        s.close();
    }

    @Test
    void eventsKeepTheirIdentityAcrossRoundBoundaries() {
        SimConfig cfg = SimConfig.defaults();
        Session s = new DefaultSession(scenarios(), eventEngines(5), cfg, 7L);
        s.advance(0.5);   // round 1 finishes with one (still undrained) event

        // Pump through the intermission into round 2 without draining.
        pump(s, () -> s.view().roundNumber() == 2 && s.view().phase() == Phase.RUNNING);

        List<SessionEvent> pending = s.view().events();
        assertTrue(pending.size() >= 1, "round 1's event must survive the round swap");
        assertEquals(1, pending.get(0).roundNumber(),
                "an undrained event must keep its round-N identity");
        assertEquals(1, pending.get(0).sequence());

        s.drainEvents();
        s.advance(0.5);   // 5 ticks -> round 2 finishes, one more event
        List<SessionEvent> round2Events = s.drainEvents();
        assertEquals(1, round2Events.size());
        assertEquals(2, round2Events.get(0).roundNumber());
        assertTrue(round2Events.get(0).sequence() > 1, "sequences increase across rounds");
        s.close();
    }

    @Test
    void generationFailureIsSurfacedAndRetrySucceeds() {
        SimConfig cfg = SimConfig.defaults();
        AtomicBoolean allowRound2 = new AtomicBoolean(false);
        ScenarioFactory failing = (masterSeed, roundNumber) -> {
            if (roundNumber > 1 && !allowRound2.get()) {
                throw new IllegalStateException("simulated terrain generation failure");
            }
            return scenarios().create(masterSeed, roundNumber);
        };

        // Rounds are long (20 ticks) so a single advance(0.5 s) == 5 ticks can
        // never skip a whole round, and the pump can observe each RUNNING state.
        Session s = new DefaultSession(failing, eventEngines(20), cfg, 11L);
        // Round 1 (finishes at tick 20), then intermission, then GENERATING
        // waiting for the (failing) round 2.
        pump(s, () -> s.view().phase() == Phase.GENERATING);
        assertEquals(Phase.GENERATING, s.view().phase());
        assertNotNull(s.view().generation().failure(), "the failure must be surfaced");
        assertEquals(2, s.view().generation().nextRound());
        assertEquals(1, s.view().roundNumber(), "the live round must not change while waiting");

        // Let the generation succeed and retry explicitly.
        allowRound2.set(true);
        s.retryGeneration();
        pump(s, () -> s.view().roundNumber() == 2 && s.view().phase() == Phase.RUNNING);
        assertEquals(Phase.RUNNING, s.view().phase());
        assertEquals(2, s.view().roundNumber());
        s.close();
    }

    /** Keeps calling {@code advance(0.5 s)} until the condition holds or the timeout elapses. */
    private static void pump(Session s, java.util.function.BooleanSupplier done) {
        long deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000L;
        while (!done.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                return;   // let the assertion below fail with a clear message
            }
            s.advance(0.5);
        }
    }
}