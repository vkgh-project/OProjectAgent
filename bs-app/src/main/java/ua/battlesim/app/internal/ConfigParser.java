package ua.battlesim.app.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

import ua.battlesim.api.SimConfig;

/**
 * Parses the command line and the {@code battle-sim.properties} file into an
 * {@link AppConfig}.
 *
 * <p>Recognised flags (all optional, order-independent):
 * <ul>
 *   <li>{@code --seed N}     master seed (default {@code 1})</li>
 *   <li>{@code --windowed}   open the windowed render loop (default: headless)</li>
 *   <li>{@code --config FILE} the properties file to read (default:
 *       {@code battle-sim.properties} in the working directory, when present)</li>
 * </ul>
 *
 * <p>Recognised properties (all optional; the standard defaults apply when
 * absent):
 * <ul>
 *   <li>{@code mapSize}       map side length in metres, finite and &gt; 0
 *       (default {@code 2000})</li>
 *   <li>{@code weatherEnabled} {@code true}/{@code false} (default {@code false})</li>
 *   <li>{@code unitsPerSide}  forced unit scale: exactly N units per side,
 *       {@code >= 0} (default {@code 0} = the standard {@code 1..20} range)</li>
 *   <li>{@code maxRoundTicks} round length bound in ticks, {@code >= 1}
 *       (default {@code 18000})</li>
 * </ul>
 *
 * <p>All failures surface as {@link IllegalArgumentException} with a precise
 * message; the caller maps them to the {@code 64} exit code.
 */
public final class ConfigParser {

    /** Default name of the properties file looked up in the working directory. */
    public static final String DEFAULT_PROPERTIES_FILE = "battle-sim.properties";

    /** Property key: map side length in metres. */
    public static final String KEY_MAP_SIZE = "mapSize";

    /** Property key: weather on/off. */
    public static final String KEY_WEATHER_ENABLED = "weatherEnabled";

    /** Property key: forced unit count per side (0 = the standard range). */
    public static final String KEY_UNITS_PER_SIDE = "unitsPerSide";

    /** Property key: round length bound in ticks. */
    public static final String KEY_MAX_ROUND_TICKS = "maxRoundTicks";

    /** Standard step in simulated seconds per tick (time = tick x 0.1 s). */
    static final double DEFAULT_STEP_SECONDS = 0.1;

    /** Standard attack interval in ticks. */
    static final int DEFAULT_ATTACK_INTERVAL_TICKS = 20;

    /** Standard ready ticks. */
    static final int DEFAULT_READY_TICKS = 20;

    /** Standard round length bound in ticks. */
    static final int DEFAULT_MAX_ROUND_TICKS = 18000;

    /** Standard minimum units per side. */
    static final int DEFAULT_MIN_UNITS = 1;

    /** Standard maximum units per side. */
    static final int DEFAULT_MAX_UNITS = 20;

    /** Default map side length in metres. */
    static final double DEFAULT_MAP_SIZE = 2000.0;

    private ConfigParser() {
        throw new AssertionError("ConfigParser is a static utility class");
    }

    /**
     * Parses the command line (and the properties file it points at) into an
     * {@link AppConfig}.
     *
     * @param args the raw program arguments (non-null, may be empty)
     * @return the resolved configuration
     * @throws IllegalArgumentException on an unknown flag, a missing/invalid
     *         value or an invalid/unreadable config file
     */
    public static AppConfig parse(List<String> args) {
        Objects.requireNonNull(args, "args");
        long seed = 1;
        boolean windowed = false;
        Path configPath = null;
        int i = 0;
        while (i < args.size()) {
            String flag = Objects.requireNonNull(args.get(i), "argument");
            switch (flag) {
                case "--seed" -> {
                    seed = longArg(args, i + 1, flag);
                    i += 2;
                }
                case "--windowed" -> {
                    windowed = true;
                    i += 1;
                }
                case "--config" -> {
                    configPath = Path.of(value(args, i + 1, flag));
                    i += 2;
                }
                default -> throw new IllegalArgumentException(
                        "unknown option '" + flag + "' (expected --seed/--windowed/--config)");
            }
        }

        Properties props = new Properties();
        if (configPath != null) {
            loadProperties(configPath, props);
        } else if (Files.isRegularFile(Path.of(DEFAULT_PROPERTIES_FILE))) {
            loadProperties(Path.of(DEFAULT_PROPERTIES_FILE), props);
        }

        return new AppConfig(seed, windowed, configPath, simConfig(props));
    }

    /**
     * Builds the round configuration from parsed properties (absent keys fall
     * back to the standard defaults).
     *
     * @param props the parsed properties (non-null, may be empty)
     * @return the resolved {@link SimConfig}
     * @throws IllegalArgumentException on an invalid property value
     */
    public static SimConfig simConfig(Properties props) {
        Objects.requireNonNull(props, "props");
        double mapSize = props.containsKey(KEY_MAP_SIZE)
                ? positiveDoubleProp(props, KEY_MAP_SIZE)
                : DEFAULT_MAP_SIZE;
        boolean weatherEnabled = props.containsKey(KEY_WEATHER_ENABLED)
                ? boolProp(props, KEY_WEATHER_ENABLED)
                : false;
        int unitsPerSide = props.containsKey(KEY_UNITS_PER_SIDE)
                ? nonNegativeIntProp(props, KEY_UNITS_PER_SIDE)
                : 0;
        int maxRoundTicks = props.containsKey(KEY_MAX_ROUND_TICKS)
                ? positiveIntProp(props, KEY_MAX_ROUND_TICKS)
                : DEFAULT_MAX_ROUND_TICKS;
        return simConfig(mapSize, weatherEnabled, unitsPerSide, maxRoundTicks);
    }

    /**
     * Builds the round configuration from raw values.
     *
     * @param mapSize        map side length in metres, finite and &gt; 0
     * @param weatherEnabled weather on/off
     * @param unitsPerSide   forced units per side, {@code >= 0}
     *                       ({@code 0} = the standard {@code 1..20} range)
     * @param maxRoundTicks  round length bound in ticks, {@code >= 1}
     * @return the round configuration
     * @throws IllegalArgumentException on an out-of-range value
     */
    public static SimConfig simConfig(double mapSize, boolean weatherEnabled,
            int unitsPerSide, int maxRoundTicks) {
        if (unitsPerSide < 0) {
            throw new IllegalArgumentException("unitsPerSide must be >= 0: " + unitsPerSide);
        }
        int min = unitsPerSide >= 1 ? unitsPerSide : DEFAULT_MIN_UNITS;
        int max = unitsPerSide >= 1 ? unitsPerSide : DEFAULT_MAX_UNITS;
        return new SimConfig(mapSize, DEFAULT_STEP_SECONDS, DEFAULT_ATTACK_INTERVAL_TICKS,
                maxRoundTicks, DEFAULT_READY_TICKS, min, max, weatherEnabled);
    }

    private static void loadProperties(Path path, Properties target) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("config file does not exist: " + path.toAbsolutePath());
        }
        try (InputStream in = Files.newInputStream(path)) {
            target.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot read config file " + path.toAbsolutePath(), e);
        }
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
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("value for " + flag + " must be an integer: " + raw, e);
        }
    }

    private static double positiveDoubleProp(Properties props, String key) {
        String raw = props.getProperty(key);
        double value;
        try {
            value = Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number: " + raw, e);
        }
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(key + " must be finite and > 0 (metres): " + raw);
        }
        return value;
    }

    private static int nonNegativeIntProp(Properties props, String key) {
        String raw = props.getProperty(key);
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer: " + raw, e);
        }
        if (value < 0) {
            throw new IllegalArgumentException(key + " must be >= 0: " + raw);
        }
        return value;
    }

    private static int positiveIntProp(Properties props, String key) {
        String raw = props.getProperty(key);
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be an integer: " + raw, e);
        }
        if (value < 1) {
            throw new IllegalArgumentException(key + " must be >= 1: " + raw);
        }
        return value;
    }

    private static boolean boolProp(Properties props, String key) {
        String raw = Objects.requireNonNull(props.getProperty(key), key);
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true")) {
            return true;
        }
        if (normalized.equals("false")) {
            return false;
        }
        throw new IllegalArgumentException(key + " must be true or false: " + raw);
    }
}