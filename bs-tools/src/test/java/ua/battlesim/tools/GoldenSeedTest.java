package ua.battlesim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.api.Outcome;

/**
 * Golden-seed determinism test: for master seeds 1..5 the round-1 outcome and
 * state hash are stored in {@code /ua/battlesim/tools/golden-seeds.txt} and the
 * test fails when any of them changes (a non-deterministic or behavioural
 * change in the simulation pipeline).
 */
final class GoldenSeedTest {

    private static final String RESOURCE = "/ua/battlesim/tools/golden-seeds.txt";

    record Golden(long seed, Outcome outcome, long stateHash) {
    }

    @Test
    void matchesStoredGoldenSeeds() throws IOException {
        List<Golden> stored = loadGolden();
        assertFalse(stored.isEmpty(), "golden-seeds.txt must contain at least one entry");
        for (int s = 1; s <= 5; s++) {
            Golden expected = bySeed(stored, s);
            RoundResult actual = new BattleSimRunner(s, 1, BattleSimRunner.goldenConfig()).play(1);
            assertEquals(expected.outcome(), actual.outcome(),
                    "seed " + s + ": outcome must not change");
            assertEquals(expected.stateHash(), actual.stateHash(),
                    "seed " + s + ": stateHash must not change");
        }
    }

    @Test
    void goldenSeedsAreReproducibleWithinARunner() throws IOException {
        // Reproducibility of the golden seeds on a single runner instance
        // (the per-unit movement state must not leak between plays).
        BattleSimRunner runner = new BattleSimRunner(1, 1, BattleSimRunner.goldenConfig());
        RoundResult a = runner.play(1);
        RoundResult b = runner.play(1);
        assertEquals(a.stateHash(), b.stateHash(), "repeated plays of the same round must agree");
        assertEquals(a.outcome(), b.outcome(), "repeated plays must give the same outcome");
    }

    private static Golden bySeed(List<Golden> stored, long seed) {
        for (Golden g : stored) {
            if (g.seed() == seed) {
                return g;
            }
        }
        fail("no golden entry for seed " + seed);
        return null;
    }

    static List<Golden> loadGolden() throws IOException {
        try (InputStream in = GoldenSeedTest.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("missing resource " + RESOURCE);
            }
            List<Golden> out = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] p = line.split("\\t");
                    if (p.length != 3) {
                        continue;
                    }
                    out.add(new Golden(Long.parseLong(p[0]), Outcome.valueOf(p[1].trim()),
                            Long.parseLong(p[2].trim())));
                }
            }
            return out;
        }
    }
}
