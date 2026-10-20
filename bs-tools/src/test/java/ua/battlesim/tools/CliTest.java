package ua.battlesim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests of the headless CLI: option parsing, the golden and report modes,
 * and error handling (all exercised through {@link BattleSimRunner#runCli} so
 * no JVM is terminated).
 */
final class CliTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream outBuf;
    private ByteArrayOutputStream errBuf;

    @BeforeEach
    void captureStreams() {
        outBuf = new ByteArrayOutputStream();
        errBuf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outBuf, true));
        System.setErr(new PrintStream(errBuf, true));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private String out() {
        return outBuf.toString(StandardCharsets.UTF_8).trim();
    }

    private String err() {
        return errBuf.toString(StandardCharsets.UTF_8).trim();
    }

    @Test
    void parseDefaults() {
        ToolOptions o = ToolOptions.parse(List.of());
        assertEquals(1, o.seed());
        assertEquals(5, o.rounds());
        assertFalse(o.golden());
        assertFalse(o.perf());
    }

    @Test
    void parseAllFlags() {
        ToolOptions o = ToolOptions.parse(List.of("--seed", "9", "--rounds", "2", "--units", "20",
                "--max-ticks", "100", "--report", "out.txt", "--golden"));
        assertEquals(9, o.seed());
        assertEquals(2, o.rounds());
        assertEquals(20, o.unitsPerSide());
        assertEquals(100, o.maxTicks());
        assertEquals(Path.of("out.txt"), o.reportPath());
        assertTrue(o.golden());
        assertEquals(100, o.config().maxRoundTicks());
        assertEquals(20, o.config().minUnits());
        assertEquals(20, o.config().maxUnits());
    }

    @Test
    void parseRejectsUnknownFlag() {
        assertThrows(IllegalArgumentException.class, () -> ToolOptions.parse(List.of("--bogus")));
    }

    @Test
    void parseRejectsMissingValue() {
        assertThrows(IllegalArgumentException.class, () -> ToolOptions.parse(List.of("--seed")));
    }

    @Test
    void goldenModePrintsFiveSeeds() {
        int code = BattleSimRunner.runCli(List.of("--golden"));
        assertEquals(0, code);
        String text = out();
        assertEquals(5, text.split("\n").length, "golden mode must print 5 seed lines");
        assertTrue(text.contains("DRAW_TIME") || text.contains("BLUE_WIN") || text.contains("RED_WIN"),
                "golden lines must contain an outcome");
    }

    @Test
    void unknownOptionReportsUsageAndErrorCode() {
        int code = BattleSimRunner.runCli(List.of("--nope"));
        assertEquals(64, code);
        assertTrue(err().contains("usage"), "must print usage on bad options");
    }

    @Test
    void reportModeWritesTable(@TempDir Path tmp) throws java.io.IOException {
        Path file = tmp.resolve("report.txt");
        int code = BattleSimRunner.runCli(List.of("--seed", "11", "--rounds", "2",
                "--max-ticks", "80", "--report", file.toString()));
        assertEquals(0, code);
        String text = Files.readString(file);
        assertTrue(text.contains("Battle Sim headless run"), "report must contain the header");
        assertTrue(text.contains("determinism: OK"), "report must report determinism OK");
        assertTrue(text.contains("ticks/s"), "report must report the throughput");
    }
}

