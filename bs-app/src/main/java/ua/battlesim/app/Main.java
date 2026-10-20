package ua.battlesim.app;

import java.util.Arrays;

import ua.battlesim.app.internal.AppConfig;
import ua.battlesim.app.internal.ConfigParser;
import ua.battlesim.app.internal.HeadlessGame;
import ua.battlesim.app.internal.WindowedGame;

/**
 * Entry point of the Battle Sim application (bs-app, the composition root).
 *
 * <p>Behaviour:
 * <ul>
 *   <li>{@code --seed N} — the deterministic master seed (default {@code 1});</li>
 *   <li>{@code --windowed} — open the OpenGL window and run the render loop
 *       (without it the app plays its headless path and exits);</li>
 *   <li>{@code --config FILE} — a {@code battle-sim.properties} style file with
 *       the map size, the weather on/off switch and the unit scale.</li>
 * </ul>
 *
 * <p>Exit codes: {@code 0} on success, {@code 2} when the headless
 * determinism check fails, {@code 64} on invalid command-line input.
 *
 * <p>Determinism: the seed drives every round; no unseeded randomness. The
 * only wall-clock use in headless mode is measuring, never feeding, state.
 */
public final class Main {

    private Main() {
    }

    /** JVM entry point: runs the app and exits with its exit code. */
    public static void main(String[] args) {
        System.exit(run(args));
    }

    /**
     * Runs the application without calling {@link System#exit} (so tests can
     * drive it in-process).
     *
     * @param args the program arguments (see {@link #usage()})
     * @return the exit code: {@code 0} on success, {@code 2} on a determinism
     *         failure, {@code 64} on invalid input
     */
    public static int run(String[] args) {
        try {
            AppConfig config = ConfigParser.parse(Arrays.asList(args));
            if (config.windowed()) {
                try (WindowedGame game = new WindowedGame(config.seed(), config.simConfig())) {
                    game.run();
                }
                return 0;
            }
            HeadlessGame game = new HeadlessGame(config.seed(), HeadlessGame.DEFAULT_ROUNDS, config.simConfig());
            HeadlessGame.RunReport report = game.run();
            System.out.print(HeadlessGame.render(report));
            return report.deterministic() ? 0 : 2;
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println(usage());
            return 64;
        }
    }

    /** @return the command-line usage text */
    public static String usage() {
        return "usage: battle-sim-app [--seed N] [--windowed] [--config FILE]";
    }
}