package ua.battlesim.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import ua.battlesim.app.internal.AppConfig;
import ua.battlesim.app.internal.ConfigParser;

/**
 * Unit tests of the command-line and properties parsing (pure logic, no
 * neighbouring-module fakes needed — the parser only touches {@code java.io}
 * and the bs-api {@code SimConfig} contract).
 */
final class ConfigParserTest {

    @Test
    void defaultsWhenNoArgsAndNoFile() {
        AppConfig cfg = ConfigParser.parse(List.of());
        assertEquals(1, cfg.seed(), "the master seed defaults to 1");
        assertFalse(cfg.windowed(), "the default mode is headless");
        assertNull(cfg.configPath(), "no explicit config file by default");
        assertEquals(2000.0, cfg.simConfig().mapSize(), "standard map size");
        assertFalse(cfg.simConfig().weatherEnabled(), "weather off by default");
        assertEquals(1, cfg.simConfig().minUnits(), "standard unit range low bound");
        assertEquals(20, cfg.simConfig().maxUnits(), "standard unit range high bound");
        assertEquals(18000, cfg.simConfig().maxRoundTicks(), "standard round bound");
        assertEquals(0.1, cfg.simConfig().stepSeconds(), "standard tick step");
    }

    @Test
    void parsesSeedWindowedAndConfigFile() throws Exception {
        Path props = tempProps("mapSize=1234\nweatherEnabled=true\nunitsPerSide=7\nmaxRoundTicks=900\n");
        AppConfig cfg = ConfigParser.parse(
                List.of("--seed", "42", "--windowed", "--config", props.toString()));

        assertEquals(42, cfg.seed());
        assertTrue(cfg.windowed());
        assertEquals(props, cfg.configPath());
        assertEquals(1234.0, cfg.simConfig().mapSize(), "map size from the file");
        assertTrue(cfg.simConfig().weatherEnabled(), "weather on/off from the file");
        assertEquals(7, cfg.simConfig().minUnits(), "unit scale fixes the low bound");
        assertEquals(7, cfg.simConfig().maxUnits(), "unit scale fixes the high bound");
        assertEquals(900, cfg.simConfig().maxRoundTicks());
    }

    @Test
    void unitScaleZeroKeepsTheStandardRange() throws Exception {
        Path props = tempProps("unitsPerSide=0\n");
        AppConfig cfg = ConfigParser.parse(List.of("--config", props.toString()));
        assertEquals(1, cfg.simConfig().minUnits());
        assertEquals(20, cfg.simConfig().maxUnits());
    }

    @Test
    void unknownKeyIsIgnored() throws Exception {
        Path props = tempProps("someOtherKey=irrelevant\nmapSize=900\n");
        AppConfig cfg = ConfigParser.parse(List.of("--config", props.toString()));
        assertEquals(900.0, cfg.simConfig().mapSize());
    }

    @Test
    void rejectsUnknownFlagsAndBadValues() {
        assertThrows(IllegalArgumentException.class, () -> ConfigParser.parse(List.of("--bogus")));
        assertThrows(IllegalArgumentException.class, () -> ConfigParser.parse(List.of("--seed")));
        assertThrows(IllegalArgumentException.class, () -> ConfigParser.parse(List.of("--seed", "abc")));
        assertThrows(IllegalArgumentException.class,
                () -> ConfigParser.parse(List.of("--config", "missing-xyz-9876.properties")));
    }

    @Test
    void rejectsInvalidProperties() throws Exception {
        for (String bad : List.of(
                "mapSize=0",
                "mapSize=NaN",
                "weatherEnabled=maybe",
                "unitsPerSide=-3",
                "unitsPerSide=two",
                "maxRoundTicks=0")) {
            Path props = tempProps(bad + "\n");
            assertThrows(IllegalArgumentException.class,
                    () -> ConfigParser.parse(List.of("--config", props.toString())),
                    "must reject: " + bad);
        }
    }

    private static Path tempProps(String content) throws Exception {
        Path props = Files.createTempFile("battle-sim", ".properties");
        Files.writeString(props, content);
        return props;
    }
}