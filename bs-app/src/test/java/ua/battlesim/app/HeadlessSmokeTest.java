package ua.battlesim.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.SimConfig;
import ua.battlesim.app.internal.HeadlessGame;

/**
 * Smoke test: the headless path of the application (the terrain pipeline,
 * scenario, combat, movement and engine wired by bs-app) plays
 * {@value #ROUNDS} full rounds for one master seed, and the replay produces
 * identical outcomes and state hashes (determinism).
 */
final class HeadlessSmokeTest {

    private static final int ROUNDS = HeadlessGame.DEFAULT_ROUNDS;

    /** Small, bounded round configuration so the smoke test stays fast. */
    private static final SimConfig CFG = new SimConfig(800.0, 0.1, 20, 400, 20, 1, 4, false);

    @Test
    void headlessPathPlaysThreeRoundsDeterministically() {
        HeadlessGame game = new HeadlessGame(2026, ROUNDS, CFG);
        HeadlessGame.RunReport report = game.run();

        assertEquals(ROUNDS, report.rounds());
        assertEquals(ROUNDS, report.firstPass().size(), "every round must be played");
        assertEquals(ROUNDS, report.secondPass().size(), "the replay must play the same rounds");
        assertTrue(report.deterministic(),
                "both passes must produce identical outcomes and state hashes");

        for (int i = 0; i < ROUNDS; i++) {
            HeadlessGame.RoundResult first = report.firstPass().get(i);
            HeadlessGame.RoundResult second = report.secondPass().get(i);
            assertEquals(i + 1, first.round(), "rounds are played in ascending order");
            assertNotNull(first.outcome(), "a finished round must have an outcome");
            assertTrue(first.ticks() > 0, "a round must run at least one tick");
            assertEquals(i + 1, second.round());
            assertEquals(first.outcome(), second.outcome(), "outcome must be deterministic");
            assertEquals(first.stateHash(), second.stateHash(), "state hash must be deterministic");
            assertEquals(first.ticks() * CFG.stepSeconds(), first.simulatedSeconds(), 1e-9);
        }
        assertNotNull(HeadlessGame.render(report), "the report must render to text");
    }

    @Test
    void mainRunIsHeadlessByDefaultAndPasses() throws Exception {
        Path props = Files.createTempFile("battle-sim", ".properties");
        Files.writeString(props, "mapSize=800\nweatherEnabled=false\nunitsPerSide=2\nmaxRoundTicks=400\n");
        int code = Main.run(new String[] { "--seed", "7", "--config", props.toString() });
        assertEquals(0, code, "the headless 3-round run must pass its determinism check");
    }
}