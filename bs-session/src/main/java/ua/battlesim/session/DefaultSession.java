package ua.battlesim.session;

import static java.lang.Double.isFinite;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ua.battlesim.api.Engine;
import ua.battlesim.api.EngineFactory;
import ua.battlesim.api.GenerationStatus;
import ua.battlesim.api.Outcome;
import ua.battlesim.api.Phase;
import ua.battlesim.api.RenderSnapshot;
import ua.battlesim.api.RoundSetup;
import ua.battlesim.api.ScenarioFactory;
import ua.battlesim.api.Session;
import ua.battlesim.api.SessionEvent;
import ua.battlesim.api.SessionView;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.SimEvent;
import ua.battlesim.api.Side;
import ua.battlesim.api.Stats;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitView;

/**
 * Default {@link Session} implementation.
 *
 * <p>Owns a single {@link Engine} (the engine is created and advanced only on
 * the thread calling {@link #advance(double)} — one simulation worker), keeps
 * the <em>next</em> round warm on one daemon worker thread (which only ever
 * calls {@link ScenarioFactory}/{@link EngineFactory} and never touches a live
 * engine), and exposes an immutable {@link SessionView} to the render thread.
 *
 * <p>Fixed timestep: {@code advance(realSeconds)} accumulates
 * {@code realSeconds x speed} and runs whole {@code stepSeconds} ticks, at most
 * {@value #MAX_TICKS_PER_ADVANCE} per call (the remainder is dropped so the
 * call stays bounded and the interpolation {@code alpha = accumulator / step}
 * always stays within {@code [0, 1)}). While paused, nothing advances.
 *
 * <p>Phase machine: a round runs until the engine reports
 * {@link Engine#finished()}, then the session spends {@value #INTERMISSION_TICKS}
 * ticks in {@link Phase#INTERMISSION}; once that elapses it swaps in the
 * prefetched round (staying in the intermission if the worker has not produced
 * it yet) and keeps one round ahead forever.
 *
 * <p>Determinism: the simulation is a pure function of its inputs; the only
 * wall-clock influence is how fast ticks are produced. No unseeded randomness
 * and no hash-order dependence (rounds are always taken in ascending order).
 */
public final class DefaultSession implements Session, AutoCloseable {

    /** Fixed simulated length of the intermission between two rounds, in ticks. */
    static final int INTERMISSION_TICKS = 80;

    /** Hard cap on the number of ticks a single {@link #advance(double)} may run. */
    static final int MAX_TICKS_PER_ADVANCE = 300;

    /** Tolerance for the fixed-step division, absorbing double rounding error. */
    static final double STEP_EPSILON = 1e-9;

    private final ScenarioFactory scenarioFactory;
    private final EngineFactory engineFactory;
    private final SimConfig cfg;
    private final long masterSeed;
    private final double step;
    private final ExecutorService worker;
    private final Object lock = new Object();

    // Mutable session state (guarded by {@link #lock}).
    private Phase phase;
    private int roundNumber;
    private Terrain terrain;
    private Engine engine;
    private double accumulator;
    private boolean paused;
    private double speed = 1.0;
    private final List<SessionEvent> pendingEvents = new ArrayList<>();
    private long nextSequence = 1L;
    private int intermissionTicks;
    private int roundTicks;
    // Aggregate statistics (guarded by {@link #lock}).
    private int roundsCompleted;
    private int blueWins;
    private int redWins;
    private int draws;
    private int timeDraws;
    private Outcome lastOutcome;
    private double lastDurationSeconds;

    // Prefetch handoff between the daemon worker and the session. Guarded by
    // its own monitor so the worker and the UI thread can never deadlock: the
    // worker only ever takes this monitor, never {@link #lock}.
    private final Object handoff = new Object();
    private Prefetch prefetched;
    private boolean prefetchPending;
    private String generationFailure;
    private boolean closed;

    /**
     * Creates a session whose round 1 is built synchronously (so {@link #view()}
     * is valid immediately) and whose round 2 is already being generated on the
     * daemon worker thread.
     *
     * @param scenarioFactory builds each round's world/units/weather (non-null)
     * @param engineFactory   starts each round's engine (non-null)
     * @param cfg             the round configuration (non-null)
     * @param masterSeed      the deterministic master seed of the session
     */
    public DefaultSession(ScenarioFactory scenarioFactory, EngineFactory engineFactory,
            SimConfig cfg, long masterSeed) {
        this.scenarioFactory = Objects.requireNonNull(scenarioFactory, "scenarioFactory");
        this.engineFactory = Objects.requireNonNull(engineFactory, "engineFactory");
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.masterSeed = masterSeed;
        this.step = cfg.stepSeconds();
        this.worker = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "bs-session-worker");
            thread.setDaemon(true);
            return thread;
        });

        // Round 1 is created on the calling thread so the very first view() is
        // valid; round 2 is handed to the daemon worker thread right away.
        RoundSetup first = scenarioFactory.create(masterSeed, 1);
        Engine firstEngine = engineFactory.start(first, cfg);
        synchronized (lock) {
            this.phase = Phase.RUNNING;
            this.roundNumber = 1;
            this.terrain = first.terrain();
            this.engine = firstEngine;
            for (SimEvent event : firstEngine.drainEvents()) {
                this.pendingEvents.add(new SessionEvent(1, this.nextSequence++, event));
            }
        }
        submitPrefetchIfNeeded(2);
    }

    @Override
    public void advance(double realSeconds) {
        if (!isFinite(realSeconds) || realSeconds < 0.0) {
            throw new IllegalArgumentException("realSeconds must be finite and >= 0: " + realSeconds);
        }
        synchronized (lock) {
            if (paused) {
                return;
            }
            double simulated = realSeconds * speed;
            double maxSimulated = (MAX_TICKS_PER_ADVANCE + 1) * step;
            if (simulated > maxSimulated) {
                simulated = maxSimulated;   // we can only ever run the cap this call
            }
            accumulator += simulated;
            long available = (long) StrictMath.floor(accumulator / step + STEP_EPSILON);
            long toRun = Math.min(available, MAX_TICKS_PER_ADVANCE);
            for (long i = 0; i < toRun; i++) {
                tickOnce();
            }
            // Drop the excess whole ticks so the call stays bounded and alpha
            // remains within [0, 1); keep only the sub-step remainder.
            double remainder = accumulator - available * step;
            if (remainder < 0.0 || remainder >= step) {
                remainder = 0.0;
            }
            accumulator = remainder;
        }
    }

    @Override
    public void setPaused(boolean paused) {
        synchronized (lock) {
            this.paused = paused;
        }
    }

    @Override
    public void setSpeed(double speed) {
        if (!isFinite(speed) || speed < 0.0) {
            throw new IllegalArgumentException("speed must be finite and >= 0: " + speed);
        }
        synchronized (lock) {
            this.speed = speed;
        }
    }

    @Override
    public SessionView view() {
        if (closed) {
            throw new IllegalStateException("session is closed");
        }
        synchronized (lock) {
            RenderSnapshot snapshot = engine.snapshot();
            int blue = 0;
            int red = 0;
            for (UnitView u : snapshot.units()) {
                if (u.state() != UnitState.DESTROYED && u.health() > 0) {
                    if (u.side() == Side.BLUE) {
                        blue++;
                    } else {
                        red++;
                    }
                }
            }
            // Read-only: pending events stay pending until drainEvents().
            List<SessionEvent> events = List.copyOf(pendingEvents);
            GenerationStatus generation = generationStatus();
            return new SessionView(phase, roundNumber, terrain, snapshot,
                    accumulator / step, new Stats(blue, red), events, paused, speed, generation);
        }
    }

    @Override
    public List<SessionEvent> drainEvents() {
        if (closed) {
            throw new IllegalStateException("session is closed");
        }
        synchronized (lock) {
            List<SessionEvent> out = List.copyOf(pendingEvents);
            pendingEvents.clear();
            return out;
        }
    }

    @Override
    public void retryGeneration() {
        synchronized (lock) {
            synchronized (handoff) {
                generationFailure = null;
                if (prefetchPending) {
                    return;   // a generation attempt is already in flight
                }
            }
            submitPrefetchIfNeeded(roundNumber + 1);
        }
    }

    /**
     * The current background-generation status (guarded by {@link #handoff}).
     */
    private GenerationStatus generationStatus() {
        synchronized (handoff) {
            return new GenerationStatus(roundNumber + 1, prefetchPending, generationFailure);
        }
    }

    /** Releases the daemon worker thread and stops background generation. */
    @Override
    public void close() {
        synchronized (lock) {
            closed = true;
        }
        worker.shutdownNow();
    }

    /**
     * @return an immutable snapshot of the session-wide aggregate statistics
     *         (rounds completed and how they finished); safe from the render thread
     */
    public SessionStats stats() {
        synchronized (lock) {
            return new SessionStats(roundsCompleted, blueWins, redWins, draws, timeDraws,
                    lastOutcome, lastDurationSeconds);
        }
    }


    /** Advances the session by exactly one fixed step (called while holding {@link #lock}). */
    private void tickOnce() {
        switch (phase) {
            case RUNNING -> {
                engine.tick();
                roundTicks++;
                for (SimEvent event : engine.drainEvents()) {
                    pendingEvents.add(new SessionEvent(roundNumber, nextSequence++, event));
                }
                if (engine.finished()) {
                    recordOutcome(engine.outcome());
                    phase = Phase.INTERMISSION;
                    intermissionTicks = INTERMISSION_TICKS;
                }
            }
            case INTERMISSION -> {
                if (intermissionTicks > 0) {
                    intermissionTicks--;
                } else {
                    // The intermission has elapsed: adopt the round as soon as
                    // it is ready, otherwise wait in GENERATING.
                    Prefetch ready = takePrefetch();
                    if (ready == null) {
                        phase = Phase.GENERATING;
                        submitPrefetchIfNeeded(roundNumber + 1);   // stay and wait
                    } else {
                        adoptRound(ready);
                    }
                }
            }
            case GENERATING -> {
                Prefetch ready = takePrefetch();
                if (ready == null) {
                    submitPrefetchIfNeeded(roundNumber + 1);   // still waiting
                } else {
                    adoptRound(ready);
                }
            }
            case FINISHED -> {
                // No more rounds: the session stays finished.
            }
        }
    }

    /** Records a finished round's outcome in the aggregate statistics. */
    private void recordOutcome(Outcome outcome) {
        if (outcome == null) {
            return;
        }
        roundsCompleted++;
        switch (outcome) {
            case BLUE_WIN -> blueWins++;
            case RED_WIN -> redWins++;
            case DRAW -> draws++;
            case DRAW_TIME -> timeDraws++;
        }
        lastOutcome = outcome;
        lastDurationSeconds = roundTicks * step;
    }

    /** Swaps in a prefetched round (called while holding {@link #lock}). */
    private void adoptRound(Prefetch p) {
        this.roundNumber = p.round();
        this.terrain = p.setup().terrain();
        this.engine = p.engine();
        for (SimEvent event : p.engine().drainEvents()) {
            pendingEvents.add(new SessionEvent(p.round(), nextSequence++, event));
        }
        this.roundTicks = 0;
        this.phase = Phase.RUNNING;
        submitPrefetchIfNeeded(p.round() + 1);
    }

    /**
     * Submits a generation of the given round on the daemon worker thread, but
     * only when one is not already in flight (called while holding {@link #lock}).
     */
    private void submitPrefetchIfNeeded(int targetRound) {
        synchronized (handoff) {
            if (closed) {
                return;   // the session is going away
            }
            if (prefetched != null || prefetchPending) {
                return;   // already have one, or already generating it
            }
            prefetchPending = true;
        }
        worker.execute(() -> {
            try {
                RoundSetup setup = scenarioFactory.create(masterSeed, targetRound);
                Engine engine = engineFactory.start(setup, cfg);
                synchronized (handoff) {
                    if (closed) {
                        return;   // do not publish into a closed session
                    }
                    this.prefetched = new Prefetch(targetRound, setup, engine);
                    this.prefetchPending = false;
                }
            } catch (RuntimeException e) {
                synchronized (handoff) {
                    this.generationFailure = String.valueOf(e);   // surface the failure
                    this.prefetchPending = false;   // allow a later retry
                }
            }
        });
    }

    /** @return the prefetched round, or {@code null} if none is ready yet */
    private Prefetch takePrefetch() {
        synchronized (handoff) {
            Prefetch p = this.prefetched;
            if (p != null) {
                this.prefetched = null;
            }
            return p;
        }
    }

    /** A generated round (setup plus freshly started engine) awaiting the swap. */
    private record Prefetch(int round, RoundSetup setup, Engine engine) {
    }
}
