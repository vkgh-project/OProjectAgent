package ua.battlesim.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.Outcome;
import ua.battlesim.api.Phase;
import ua.battlesim.api.Side;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.Stats;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitType;
import ua.battlesim.api.UnitView;

/** Behaviour tests for {@link DefaultSession}, using fakes of the other modules. */
class DefaultSessionTest {

    private static final long TIMEOUT_MS = 5000;

    @Test
    void fixedStepIsIndependentOfFrameRate() {
        SimConfig cfg = SimConfig.defaults();

        DefaultSession oneShot = new DefaultSession(scenario(), engines(r -> Integer.MAX_VALUE, r -> Outcome.BLUE_WIN), cfg, 42L);
        oneShot.advance(1.0);   // 10 ticks in a single call

        DefaultSession framed = new DefaultSession(scenario(), engines(r -> Integer.MAX_VALUE, r -> Outcome.BLUE_WIN), cfg, 42L);
        framed.advance(0.1);
        framed.advance(0.1);
        framed.advance(0.1);
        framed.advance(0.5);
        framed.advance(0.2);   // 1.0 s total -> 10 ticks, different chunking

        assertEquals(oneShot.view().snapshot().tick(), framed.view().snapshot().tick(),
                "the same simulated time must yield the same number of ticks");
        assertEquals(10, oneShot.view().snapshot().tick());
        assertEquals(oneShot.view().alpha(), framed.view().alpha(), 0.0);

        oneShot.close();
        framed.close();
    }

    @Test
    void speedScalesTickProduction() {
        SimConfig cfg = SimConfig.defaults();
        DefaultSession s = new DefaultSession(scenario(), engines(r -> Integer.MAX_VALUE, r -> Outcome.BLUE_WIN), cfg, 3L);
        s.setSpeed(2.0);
        s.advance(0.5);   // 0.5 * 2 = 1.0 s -> 10 ticks
        assertEquals(10, s.view().snapshot().tick());
        s.close();
    }

    @Test
    void pauseFreezesTime() {
        SimConfig cfg = SimConfig.defaults();
        DefaultSession s = new DefaultSession(scenario(), engines(r -> Integer.MAX_VALUE, r -> Outcome.BLUE_WIN), cfg, 7L);
        s.advance(0.3);   // 3 ticks
        int before = s.view().snapshot().tick();
        assertEquals(3, before);

        s.setPaused(true);
        s.advance(10.0);   // must be ignored
        assertEquals(before, s.view().snapshot().tick(), "a paused advance must not run ticks");
        assertTrue(s.view().paused());

        s.setPaused(false);
        s.advance(0.2);   // 2 ticks
        assertEquals(before + 2, s.view().snapshot().tick());
        s.close();
    }

    @Test
    void prefetchGeneratesTheNextRoundAndSwaps() {
        SimConfig cfg = SimConfig.defaults();
        FakeEngineFactory engineFactory = engines(r -> (r == 1 ? 5 : Integer.MAX_VALUE), r -> Outcome.BLUE_WIN);
        FakeScenarioFactory scenarioFactory = new FakeScenarioFactory(FakeTerrain.standard(), new FakeWeather());

        DefaultSession s = new DefaultSession(scenarioFactory, engineFactory, cfg, 99L);
        assertEquals(Phase.RUNNING, s.view().phase());
        assertEquals(1, s.view().roundNumber());

        s.advance(0.5);   // 5 ticks -> round 1 finishes -> INTERMISSION
        assertEquals(Phase.INTERMISSION, s.view().phase());

        pump(s, () -> s.view().roundNumber() == 2 && s.view().phase() == Phase.RUNNING);
        assertEquals(Phase.RUNNING, s.view().phase());
        assertEquals(2, s.view().roundNumber());
        assertTrue(engineFactory.started().contains(2), "round 2's engine must have been created");
        assertTrue(scenarioFactory.created().contains(2), "round 2's world must have been created");

        s.close();
    }

    @Test
    void tracksAggregateStatistics() {
        SimConfig cfg = SimConfig.defaults();
        FakeEngineFactory engineFactory = engines(
                r -> 5,
                r -> switch (r) {
                    case 1 -> Outcome.BLUE_WIN;
                    case 2 -> Outcome.RED_WIN;
                    case 3 -> Outcome.DRAW;
                    default -> Outcome.DRAW_TIME;
                });
        DefaultSession s = new DefaultSession(new FakeScenarioFactory(FakeTerrain.standard(), new FakeWeather()),
                engineFactory, cfg, 123L);

        pump(s, () -> s.stats().roundsCompleted() >= 4);

        SessionStats st = s.stats();
        assertEquals(4, st.roundsCompleted());
        assertEquals(1, st.blueWins());
        assertEquals(1, st.redWins());
        assertEquals(1, st.draws());
        assertEquals(1, st.timeDraws());
        assertEquals(Outcome.DRAW_TIME, st.lastOutcome());
        assertEquals(5 * cfg.stepSeconds(), st.lastDurationSeconds(), 0.0);

        // The per-round stats in the view reflect the surviving units (1 blue + 1 red).
        assertEquals(new Stats(1, 1), s.view().stats());

        s.close();
    }

    @Test
    void advanceIsBoundedToThePerCallCap() {
        SimConfig cfg = SimConfig.defaults();
        DefaultSession s = new DefaultSession(scenario(), engines(r -> Integer.MAX_VALUE, r -> Outcome.BLUE_WIN), cfg, 5L);

        long start = System.nanoTime();
        s.advance(1_000_000.0);   // 1e7 ticks worth; must be capped at 300
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertEquals(300, s.view().snapshot().tick(), "only the per-call cap of ticks may run");
        assertTrue(s.view().alpha() < 1.0, "alpha must stay within [0,1) after dropping the remainder");
        assertTrue(elapsedMs < 2000, "advance must return promptly, took " + elapsedMs + " ms");

        s.close();
    }

    // ---- helpers -----------------------------------------------------------

    private static FakeScenarioFactory scenario() {
        return new FakeScenarioFactory(FakeTerrain.standard(), new FakeWeather());
    }

    private static FakeEngineFactory engines(IntFunction<Integer> finishAfter, IntFunction<Outcome> outcome) {
        return new FakeEngineFactory(new FakeWeather(), finishAfter, outcome, sampleUnits());
    }

    private static List<UnitView> sampleUnits() {
        return List.of(
                new UnitView(0, Side.BLUE, UnitType.TANK, 100, 100, 100, 100, 0, 0, 0, 0, 100,
                        UnitState.READY, 0.0, -1),
                new UnitView(1, Side.RED, UnitType.TANK, 1900, 1900, 1900, 1900, 0, 0, 0, 0, 100,
                        UnitState.READY, 0.0, -1));
    }

    /** Keeps calling {@code advance(0.5 s)} until the condition holds or the timeout elapses. */
    private static void pump(DefaultSession s, BooleanSupplier done) {
        long deadline = System.nanoTime() + TIMEOUT_MS * 1_000_000L;
        while (!done.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                return;   // let the assertion below fail with a clear message
            }
            s.advance(0.5);
        }
    }
}