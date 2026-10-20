package ua.battlesim.tools;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import ua.battlesim.api.SimConfig;

/**
 * Parsed command-line options of the headless runner.
 *
 * <p>Recognised flags (all optional, order-independent):
 * <ul>
 *   <li>{@code --seed N}      master seed (default {@code 1})</li>
 *   <li>{@code --rounds N}    number of rounds to play, {@code >= 1} (default {@code 5})</li>
 *   <li>{@code --units N}     force exactly {@code N} units per side (min = max = N);
 *       when absent the {@link SimConfig#defaults()} unit range is used</li>
 *   <li>{@code --report FILE} write the run report to {@code FILE}</li>
 *   <li>{@code --max-ticks N} bound each round's length to {@code N} ticks
 *       (default {@code 18000})</li>
 *   <li>{@code --golden}      print the golden-seed lines (seeds 1..5, round 1)
 *       in the {@code seed \t outcome \t stateHash} format used by the golden
 *       resource, and exit (a regeneration tool)</li>
 *   <li>{@code --perf}        run a performance probe (40 units) and exit</li>
 * </ul>
 *
 * <p>Immutable and thread-safe; {@code reportPath} may be {@code null}.
 */
public record ToolOptions(long seed, int rounds, int unitsPerSide, int maxTicks,
        Path reportPath, boolean golden, boolean perf) {

    public ToolOptions {
        if (rounds < 1) {
            throw new IllegalArgumentException("rounds must be >= 1: " + rounds);
        }
        if (unitsPerSide < 0) {
            throw new IllegalArgumentException("unitsPerSide must be >= 0: " + unitsPerSide);
        }
        if (maxTicks < 1) {
            throw new IllegalArgumentException("maxTicks must be >= 1: " + maxTicks);
        }
    }

    /**
     * @return the round configuration implied by these options: the standard
     *         defaults unless a fixed unit count was requested (in which case both
     *         {@code minUnits} and {@code maxUnits} are set to that count) or the
     *         round length was bounded
     */
    public SimConfig config() {
        SimConfig base = unitsPerSide >= 1
                ? new SimConfig(2000.0, 0.1, 20, 18000, 20, unitsPerSide, unitsPerSide, false)
                : SimConfig.defaults();
        if (maxTicks == 18000) {
            return base;
        }
        return new SimConfig(base.mapSize(), base.stepSeconds(), base.attackIntervalTicks(),
                maxTicks, base.readyTicks(), base.minUnits(), base.maxUnits(), base.weatherEnabled());
    }

    /**
     * Parses the CLI into {@link ToolOptions}.
     *
     * @param args the raw program arguments (non-null, may be empty)
     * @return the parsed options
     * @throws IllegalArgumentException on an unknown flag or a missing/invalid value
     */
    public static ToolOptions parse(List<String> args) {
        Objects.requireNonNull(args, "args");
        long seed = 1;
        int rounds = 5;
        int units = 0;
        int maxTicks = 18000;
        Path report = null;
        boolean golden = false;
        boolean perf = false;
        int i = 0;
        while (i < args.size()) {
            String flag = args.get(i);
            switch (flag) {
                case "--seed" -> {
                    seed = longArg(args, i + 1, flag);
                    i += 2;
                }
                case "--rounds" -> {
                    rounds = intArg(args, i + 1, flag);
                    i += 2;
                }
                case "--units" -> {
                    units = intArg(args, i + 1, flag);
                    i += 2;
                }
                case "--max-ticks" -> {
                    maxTicks = intArg(args, i + 1, flag);
                    i += 2;
                }
                case "--report" -> {
                    report = Path.of(value(args, i + 1, flag));
                    i += 2;
                }
                case "--golden" -> {
                    golden = true;
                    i += 1;
                }
                case "--perf" -> {
                    perf = true;
                    i += 1;
                }
                default -> throw new IllegalArgumentException(
                        "unknown option '" + flag + "' (expected --seed/--rounds/--units/--max-ticks/--report/--golden/--perf)");
            }
        }
        return new ToolOptions(seed, rounds, units, maxTicks, report, golden, perf);
    }

    private static String value(List<String> args, int i, String flag) {
        if (i >= args.size()) {
            throw new IllegalArgumentException("option " + flag + " requires a value");
        }
        return Objects.requireNonNull(args.get(i), "value for " + flag);
    }

    private static long longArg(List<String> args, int i, String flag) {
        String raw = value(args, i, flag);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("value for " + flag + " must be an integer: " + raw, e);
        }
    }

    private static int intArg(List<String> args, int i, String flag) {
        long value = longArg(args, i, flag);
        if (value < 0) {
            throw new IllegalArgumentException("value for " + flag + " must be >= 0: " + value);
        }
        if (value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("value for " + flag + " is out of range: " + value);
        }
        return (int) value;
    }
}
