package ua.battlesim.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import ua.battlesim.api.CombatSystem;
import ua.battlesim.api.Engine;
import ua.battlesim.api.EngineFactory;
import ua.battlesim.api.MovementSystem;
import ua.battlesim.api.Outcome;
import ua.battlesim.api.RenderSnapshot;
import ua.battlesim.api.RoundSetup;
import ua.battlesim.api.ScenarioFactory;
import ua.battlesim.api.Session;
import ua.battlesim.api.SimConfig;
import ua.battlesim.api.Side;
import ua.battlesim.api.Terrain;
import ua.battlesim.api.TerrainService;
import ua.battlesim.api.UnitData;
import ua.battlesim.api.UnitState;
import ua.battlesim.api.UnitView;
import ua.battlesim.api.WeatherModel;
import ua.battlesim.combat.StandardCombatSystem;
import ua.battlesim.engine.DefaultEngineFactory;
import ua.battlesim.movement.StandardMovementSystem;
import ua.battlesim.scenario.StandardScenarioFactory;
import ua.battlesim.session.DefaultSession;
import ua.battlesim.tools.internal.WiredTerrainService;
import ua.battlesim.weather.SimpleWeatherModel;

/**
 * The headless Battle Sim runner (bs-tools): the composition root that wires the
 * real core modules (terrain, features, nav, weather, combat, movement,
 * scenario, engine and session) and plays rounds at maximum speed without any
 * graphics.
 *
 * <p>For a given master seed and round number the runner deterministically
 * builds the round (terrain + features + nav + units + weather), starts a
 * {@link Engine} and advances it tick-by-tick until it finishes. The same
 * {@code (seed, round)} always produces the same {@link RoundResult} and the
 * same {@code stateHash}, so a round can be played twice and the two hashes
 * compared as a determinism check.
 *
 * <p>Units: simulated time in seconds ({@code tick x stepSeconds}); wall time in
 * wall-clock seconds; throughput in simulation ticks per wall-clock second.
 *
 * <p>Determinism: the simulation itself is a pure function of its inputs (only
 * {@code DetRandom} and {@code StrictMath}); the only wall-clock use here is the
 * {@link System#nanoTime()} used to <em>measure</em> wall time, which never
 * feeds back into the simulation state.
 *
 * <p>Thread-safety: each runner is intended to be driven from a single thread;
 * the played engines are single-threaded by contract.
 */
public final class BattleSimRunner {

    private final long seed;
    private final int rounds;
    private final SimConfig config;

    private final TerrainService terrainService;
    private final ScenarioFactory scenarioFactory;
    private final EngineFactory engineFactory;
    private final CombatSystem combatSystem;
    private final MovementSystem movementSystem;

    /**
     * @param seed   the deterministic master seed of the session (any 64-bit value)
     * @param rounds the number of rounds to play in {@link #run()}, {@code >= 1}
     * @param config the round configuration (non-null)
     */
    public BattleSimRunner(long seed, int rounds, SimConfig config) {
        if (rounds < 1) {
            throw new IllegalArgumentException("rounds must be >= 1: " + rounds);
        }
        this.seed = seed;
        this.rounds = rounds;
        this.config = Objects.requireNonNull(config, "config");

        // Wire the real core modules (terrain + features + nav -> full Terrain,
        // standard combat and movement, the standard scenario factory and the
        // default engine factory). bs-tools is the layer above all of them.
        this.terrainService = new WiredTerrainService(config);
        this.scenarioFactory = new StandardScenarioFactory(terrainService, config,
                (weatherSeed, cfg) ->
                        new SimpleWeatherModel(weatherSeed, cfg.weatherEnabled(), cfg.stepSeconds()));
        this.combatSystem = new StandardCombatSystem();
        this.movementSystem = new StandardMovementSystem();
        this.engineFactory = new DefaultEngineFactory(cfg -> combatSystem, cfg -> movementSystem);
    }

    // ---- accessors to the wired components (useful for tests and the CLI) ----

    /** @return the master seed */
    public long seed() {
        return seed;
    }

    /** @return the number of rounds to play */
    public int rounds() {
        return rounds;
    }

    /** @return the round configuration */
    public SimConfig config() {
        return config;
    }

    /** @return the wired terrain service (terrain + features + nav) */
    public TerrainService terrainService() {
        return terrainService;
    }

    /** @return the wired scenario factory */
    public ScenarioFactory scenarioFactory() {
        return scenarioFactory;
    }

    /** @return the wired engine factory */
    public EngineFactory engineFactory() {
        return engineFactory;
    }

    /** @return the wired combat system */
    public CombatSystem combatSystem() {
        return combatSystem;
    }

    /** @return the wired movement system */
    public MovementSystem movementSystem() {
        return movementSystem;
    }

    /**
     * @return a fresh, fully wired {@link Session} (single owner of its engines)
     *         built from this runner's factories, config and seed; the caller is
     *         responsible for {@link Session} lifecycle ({@code close})
     */
    public Session newSession() {
        return new DefaultSession(scenarioFactory, engineFactory, config, seed);
    }

    // ---- simulation ----

    /**
     * Plays one round (deterministically) and returns the result.
     *
     * @param round the 1-based round number, {@code >= 1}
     * @return the round result (outcome, survivors, state hash, wall time)
     * @throws IllegalArgumentException if the round number is not {@code >= 1}
     */
    public RoundResult play(int round) {
        if (round < 1) {
            throw new IllegalArgumentException("round must be >= 1: " + round);
        }
        Played p = playRound(round);
        return new RoundResult(round, p.outcome, p.ticks * config.stepSeconds(),
                p.survivorsBlue, p.survivorsRed, p.stateHash, p.setupWall + p.tickWall);
    }

    /**
     * @return a fresh {@link EngineFactory} with a brand-new combat and movement
     *         system, so that each played round is fully isolated: the movement
     *         system keeps per-unit (by id) state, so it must never be shared
     *         across rounds or a later round would inherit an earlier round's
     *         motion
     */
    private EngineFactory freshEngineFactory() {
        return new DefaultEngineFactory(cfg -> new StandardCombatSystem(),
                cfg -> new StandardMovementSystem());
    }

    /**
     * Plays every round {@code 1..rounds}, running each one twice and comparing
     * the two {@code stateHash} values (the determinism check), and measures the
     * simulation throughput.
     *
     * @return the aggregate {@link RunReport}
     */
    public RunReport run() {
        List<RoundResult> results = new ArrayList<>(rounds);
        boolean determinismOk = true;
        long totalTicks = 0;
        double totalWall = 0.0;
        double totalTickWall = 0.0;
        for (int round = 1; round <= rounds; round++) {
            Played a = playRound(round);
            Played b = playRound(round);
            determinismOk &= a.stateHash == b.stateHash
                    && a.outcome == b.outcome
                    && a.ticks == b.ticks
                    && a.survivorsBlue == b.survivorsBlue
                    && a.survivorsRed == b.survivorsRed;
            totalTicks += a.ticks;
            totalWall += a.setupWall + a.tickWall;
            totalTickWall += a.tickWall;
            results.add(new RoundResult(round, a.outcome, a.ticks * config.stepSeconds(),
                    a.survivorsBlue, a.survivorsRed, a.stateHash, a.setupWall + a.tickWall));
        }
        double tps = totalTickWall > 0.0 ? totalTicks / totalTickWall : 0.0;
        return new RunReport(seed, config, results, determinismOk, totalTicks,
                totalWall, totalTickWall, tps);
    }

    /**
     * Measures the steady-state simulation throughput: advances a fresh round
     * through {@code warmupTicks} of warm-up (initial path planning, etc.), then
     * times {@code measuredTicks} of the simulation loop.
     *
     * @param cfg            the round configuration (non-null, e.g. 20 units per side for 40 units)
     * @param warmupTicks    warm-up ticks to run before timing, {@code >= 0}
     * @param measuredTicks  ticks to time, {@code >= 1}
     * @return the measured simulation ticks per wall-clock second
     * @throws IllegalStateException if the round finishes before the measured window completes
     */
    public double measureThroughput(SimConfig cfg, int warmupTicks, int measuredTicks) {
        if (warmupTicks < 0) {
            throw new IllegalArgumentException("warmupTicks must be >= 0: " + warmupTicks);
        }
        if (measuredTicks < 1) {
            throw new IllegalArgumentException("measuredTicks must be >= 1: " + measuredTicks);
        }
        Objects.requireNonNull(cfg, "cfg");
        RoundSetup setup = scenarioFactory.create(seed, 1);
        Engine engine = freshEngineFactory().start(setup, cfg);
        for (int i = 0; i < warmupTicks; i++) {
            if (engine.finished()) {
                break;
            }
            engine.tick();
        }
        if (engine.finished()) {
            throw new IllegalStateException("round finished during warm-up");
        }
        long start = System.nanoTime();
        int ran = 0;
        while (ran < measuredTicks && !engine.finished()) {
            engine.tick();
            ran++;
        }
        long end = System.nanoTime();
        if (ran < measuredTicks) {
            throw new IllegalStateException("round finished during the measured window");
        }
        return ran / ((end - start) / 1e9);
    }

    /**
     * Plays one round on the wired factories; measures the setup (terrain,
     * features, nav, units, weather) wall time separately from the tick wall
     * time so that the throughput is reported on the simulation loop alone.
     */
    private Played playRound(int round) {
        long setupStart = System.nanoTime();
        RoundSetup setup = scenarioFactory.create(seed, round);
        Engine engine = freshEngineFactory().start(setup, config);
        double setupWall = (System.nanoTime() - setupStart) / 1e9;

        long tickStart = System.nanoTime();
        while (!engine.finished()) {
            engine.tick();
        }
        double tickWall = (System.nanoTime() - tickStart) / 1e9;

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
        return new Played(engine.outcome(), snapshot.tick(), engine.stateHash(),
                setupWall, tickWall, blue, red);
    }

    // ---- golden seeds ----

    /**
     * @return the golden line for this runner's master seed:
     *         {@code seed \t outcome \t stateHash} for round 1
     */
    public String goldenLine() {
        RoundResult r = play(1);
        return seed + "\t" + r.outcome() + "\t" + r.stateHash();
    }

    /**
     * @return the fixed, documented configuration the golden-seed test uses: the
     *         standard simulation parameters with the round length bounded so the
     *         golden suite stays fast and stable
     */
    public static SimConfig goldenConfig() {
        return new SimConfig(2000.0, 0.1, 20, 2000, 20, 1, 20, false);
    }

    /**
     * @return the golden block for master seeds {@code 1..5} (round 1, the fixed
     *         {@link #goldenConfig()}), one line each: {@code seed \t outcome \t stateHash}
     */
    public static List<String> goldenBlock() {
        SimConfig cfg = goldenConfig();
        List<String> lines = new ArrayList<>(5);
        for (long s = 1; s <= 5; s++) {
            lines.add(new BattleSimRunner(s, 1, cfg).goldenLine());
        }
        return lines;
    }

    /**
     * Runs a performance probe with {@code units} units per side and reports the
     * terrain-generation, setup, engine-start and per-tick costs (a diagnostic
     * for the {@code > 1000 ticks/s} budget with 40 units).
     *
     * @param units units per side, {@code >= 1}
     * @return the formatted probe report
     */
    public String probe(int units) {
        if (units < 1) {
            throw new IllegalArgumentException("units must be >= 1: " + units);
        }
        SimConfig cfg = new SimConfig(2000.0, 0.1, 20, 18000, 20, units, units, false);
        BattleSimRunner r = new BattleSimRunner(1, 1, cfg);
        StringBuilder sb = new StringBuilder();

        long t0 = System.nanoTime();
        Terrain terrain = r.terrainService().generate(12345L);
        long t1 = System.nanoTime();
        sb.append(String.format(Locale.ROOT,
                "terrain gen: %.3fs cells=%d trees=%d rocks=%d roads=%d%n",
                (t1 - t0) / 1e9, terrain.nav().cells(), terrain.trees().size(),
                terrain.rocks().size(), terrain.roads().size()));

        t0 = System.nanoTime();
        RoundSetup setup = r.scenarioFactory().create(1, 1);
        long t2 = System.nanoTime();
        sb.append(String.format(Locale.ROOT, "setup: %.3fs units=%d%n",
                (t2 - t0) / 1e9, setup.units().size()));

        t0 = System.nanoTime();
        Engine engine = r.freshEngineFactory().start(setup, cfg);
        long t3 = System.nanoTime();
        sb.append(String.format(Locale.ROOT, "engine start: %.3fs%n", (t3 - t0) / 1e9));

        // Warm up (initial path planning etc.) then measure steady state.
        for (int i = 0; i < 50; i++) {
            if (engine.finished()) break;
            engine.tick();
        }
        int n = 400;
        int ran = 0;
        t0 = System.nanoTime();
        while (ran < n && !engine.finished()) {
            engine.tick();
            ran++;
        }
        long t4 = System.nanoTime();
        sb.append(String.format(Locale.ROOT, "STEADY: %d ticks in %.3fs -> %.3f ms/tick, %.0f tps (finished=%b)%n",
                ran, (t4 - t0) / 1e9, (t4 - t0) / 1e6 / ran, ran / ((t4 - t0) / 1e9), engine.finished()));

        // Break down movement vs combat on a fresh round.
        RoundSetup s2 = r.scenarioFactory().create(2, 1);
        List<UnitData> unitList = s2.units();
        WeatherModel w = s2.weather();
        Terrain terrain2 = s2.terrain();
        for (int t = 1; t <= 30; t++) {
            w.step(t);
            r.movementSystem().step(t, unitList, terrain2, w);
        }
        int m = 300;
        int mt = 30;
        long mStart = System.nanoTime();
        for (int i = 0; i < m; i++) {
            mt++;
            r.movementSystem().step(mt, unitList, terrain2, w);
        }
        long mEnd = System.nanoTime();
        int ct = 30;
        long cStart = System.nanoTime();
        for (int i = 0; i < m; i++) {
            ct++;
            r.combatSystem().resolve(ct, unitList, terrain2, w);
        }
        long cEnd = System.nanoTime();
        sb.append(String.format(Locale.ROOT, "MOVE=%.3f ms/tick  COMBAT=%.3f ms/tick%n",
                (mEnd - mStart) / 1e6 / m, (cEnd - cStart) / 1e6 / m));
        return sb.toString();
    }


    // ---- CLI ----

    /**
     * The headless command-line entry point:
     * {@code --seed N --rounds N --units N --report FILE} (and {@code --golden}).
     */
    public static void main(String[] args) {
        System.exit(runCli(List.of(args)));
    }

    /**
     * Runs the CLI and returns the process exit code (kept separate from
     * {@code main} so it can be exercised without terminating the JVM).
     */
    public static int runCli(List<String> args) {
        ToolOptions opts;
        try {
            opts = ToolOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println(usage());
            return 64;
        }

        if (opts.golden()) {
            for (String line : goldenBlock()) {
                System.out.println(line);
            }
            return 0;
        }

        if (opts.perf()) {
            System.out.print(new BattleSimRunner(opts.seed(), 1, opts.config()).probe(20));
            return 0;
        }

        try {
            BattleSimRunner runner = new BattleSimRunner(opts.seed(), opts.rounds(), opts.config());
            RunReport report = runner.run();
            String text = render(report);
            System.out.print(text);
            if (opts.reportPath() != null) {
                Path target = opts.reportPath();
                Path parent = target.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(target, text);
                System.out.println("report written to " + target.toAbsolutePath());
            }
            return report.determinismOk() ? 0 : 2;
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            return 70;
        }
    }

    /**
     * @return a human-readable table of the run: round, outcome, simulated time,
     *         survivors and wall time, plus the determinism and throughput summary
     */
    public static String render(RunReport report) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("Battle Sim headless run  (seed=").append(report.seed()).append(")\n");
        sb.append("config: ").append(report.config()).append('\n');
        sb.append(String.format(Locale.ROOT, "%-6s %-10s %-14s %-10s %-10s %s%n",
                "round", "outcome", "simulated(s)", "survivors", "wall(s)", "stateHash"));
        for (RoundResult r : report.rounds()) {
            sb.append(String.format(Locale.ROOT,
                    "%-6d %-10s %-14.1f %-10d %-10.3f %d%n",
                    r.round(), r.outcome(), r.simulatedSeconds(),
                    r.survivors(), r.wallSeconds(), r.stateHash()));
        }
        sb.append("-".repeat(60)).append('\n');
        sb.append("determinism: ").append(report.determinismOk() ? "OK" : "FAIL").append('\n');
        sb.append(String.format(Locale.ROOT, "ticks: %d%n", report.totalTicks()));
        sb.append(String.format(Locale.ROOT, "wall: %.3f s%n", report.totalWallSeconds()));
        sb.append(String.format(Locale.ROOT, "throughput: %.1f ticks/s%n", report.ticksPerSecond()));
        return sb.toString();
    }

    /** @return the CLI usage text */
    public static String usage() {
        return "usage: battle-sim [--seed N] [--rounds N] [--units N] [--max-ticks N] "
                + "[--report FILE] [--golden] [--perf]";
    }

    /** The internal result of playing one round, with separate wall-time fields. */
    private static final class Played {
        final Outcome outcome;
        final int ticks;
        final long stateHash;
        final double setupWall;
        final double tickWall;
        final int survivorsBlue;
        final int survivorsRed;

        Played(Outcome outcome, int ticks, long stateHash, double setupWall, double tickWall,
                int survivorsBlue, int survivorsRed) {
            this.outcome = outcome;
            this.ticks = ticks;
            this.stateHash = stateHash;
            this.setupWall = setupWall;
            this.tickWall = tickWall;
            this.survivorsBlue = survivorsBlue;
            this.survivorsRed = survivorsRed;
        }
    }
}

