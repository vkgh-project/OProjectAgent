package ua.battlesim.app.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import ua.battlesim.api.Engine;
import ua.battlesim.api.Outcome;
import ua.battlesim.api.RoundSetup;
import ua.battlesim.api.SimConfig;

/**
 * The headless game path of the application: plays full rounds of one master
 * seed through the real core modules (terrain pipeline, scenario, combat,
 * movement, engine) and verifies determinism by replaying the same rounds and
 * comparing the outcomes and the {@link Engine#stateHash() state hashes}.
 *
 * <p>For a given master seed the same rounds (rounds 1..N) always produce the
 * same {@link RoundResult} list, because the round seed is a pure function of
 * the master seed and the round number.
 *
 * <p>Units: {@code simulatedSeconds} in simulated seconds
 * ({@code ticks x stepSeconds}).
 *
 * <p>Thread-safety: each game is driven from a single thread; the engines it
 * plays are single-threaded by contract.
 */
public final class HeadlessGame {

    /** Default number of headless rounds. */
    public static final int DEFAULT_ROUNDS = 3;

    private final long seed;
    private final int rounds;
    private final AppWiring wiring;

    /**
     * @param seed   the deterministic master seed (any 64-bit value)
     * @param rounds the number of rounds to play, {@code >= 1}
     * @param config the round configuration (non-null)
     */
    public HeadlessGame(long seed, int rounds, SimConfig config) {
        if (rounds < 1) {
            throw new IllegalArgumentException("rounds must be >= 1: " + rounds);
        }
        this.seed = seed;
        this.rounds = rounds;
        this.wiring = new AppWiring(Objects.requireNonNull(config, "config"), seed);
    }

    /**
     * @param seed   the deterministic master seed (any 64-bit value)
     * @param config the round configuration (non-null)
     */
    public HeadlessGame(long seed, SimConfig config) {
        this(seed, DEFAULT_ROUNDS, config);
    }

    /** @return the master seed */
    public long seed() {
        return seed;
    }

    /** @return the number of rounds to play */
    public int rounds() {
        return rounds;
    }

    /** @return the composition this game plays through */
    public AppWiring wiring() {
        return wiring;
    }

    /**
     * Plays all rounds twice and compares the two passes.
     *
     * @return the full report (both passes and the determinism verdict)
     */
    public RunReport run() {
        List<RoundResult> first = playPass();
        List<RoundResult> second = playPass();
        return new RunReport(seed, rounds, first, second, sameResults(first, second));
    }

    /** Plays rounds 1..N once, in ascending order. */
    private List<RoundResult> playPass() {
        List<RoundResult> results = new ArrayList<>(rounds);
        for (int round = 1; round <= rounds; round++) {
            RoundSetup setup = wiring.scenarioFactory().create(seed, round);
            Engine engine = wiring.engineFactory().start(setup, wiring.config());
            int ticks = 0;
            while (!engine.finished()) {
                engine.tick();
                ticks++;
            }
            results.add(new RoundResult(round, engine.outcome(), ticks,
                    ticks * wiring.config().stepSeconds(), engine.stateHash()));
        }
        return results;
    }

    private static boolean sameResults(List<RoundResult> a, List<RoundResult> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            RoundResult x = a.get(i);
            RoundResult y = b.get(i);
            if (x.outcome() != y.outcome() || x.stateHash() != y.stateHash()) {
                return false;
            }
        }
        return true;
    }

    /** The deterministic result of one played round. */
    public record RoundResult(int round, Outcome outcome, int ticks,
            double simulatedSeconds, long stateHash) {
    }

    /** The result of a full headless run (both passes and the verdict). */
    public record RunReport(long seed, int rounds, List<RoundResult> firstPass,
            List<RoundResult> secondPass, boolean deterministic) {
    }

    /**
     * @param report the run report (non-null)
     * @return a human-readable table: round, outcome, ticks, simulated time,
     *         state hash and the determinism verdict
     */
    public static String render(RunReport report) {
        Objects.requireNonNull(report, "report");
        StringBuilder sb = new StringBuilder(256);
        sb.append("Battle Sim (bs-app) headless run  (seed=").append(report.seed()).append(")\n");
        sb.append(String.format(Locale.ROOT, "%-6s %-10s %-10s %-14s %s%n",
                "round", "outcome", "ticks", "simulated(s)", "stateHash"));
        for (RoundResult r : report.firstPass()) {
            sb.append(String.format(Locale.ROOT, "%-6d %-10s %-10d %-14.1f %d%n",
                    r.round(), r.outcome(), r.ticks(), r.simulatedSeconds(), r.stateHash()));
        }
        sb.append("-".repeat(60)).append('\n');
        sb.append("determinism: ").append(report.deterministic() ? "OK" : "FAIL").append('\n');
        return sb.toString();
    }
}