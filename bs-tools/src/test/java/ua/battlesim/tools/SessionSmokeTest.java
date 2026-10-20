package ua.battlesim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ua.battlesim.api.Phase;
import ua.battlesim.api.Session;
import ua.battlesim.api.SessionView;
import ua.battlesim.api.SimConfig;

/**
 * Verifies that the headless runner wires a working {@link Session} (the single
 * owner of its engines) from the real core modules: the initial view is valid
 * and the session advances without error.
 */
final class SessionSmokeTest {

    private static final SimConfig CFG = new SimConfig(2000.0, 0.1, 20, 2000, 20, 1, 20, false);

    private Session session;

    @AfterEach
    void close() throws Exception {
        if (session != null) {
            ((AutoCloseable) session).close();
        }
    }

    @Test
    void initialViewIsValidAndAdvances() {
        session = new BattleSimRunner(5, 1, CFG).newSession();
        SessionView view = session.view();
        assertNotNull(view, "view must not be null");
        assertEquals(Phase.RUNNING, view.phase(), "a fresh session must be running");
        assertEquals(1, view.roundNumber(), "a fresh session must be on round 1");
        assertNotNull(view.snapshot(), "the round-1 snapshot must be present");
        assertNotNull(view.terrain(), "the round-1 terrain must be present");

        session.setSpeed(1000.0);
        session.advance(0.01);   // 10 s of simulated time -> a hundred ticks
        SessionView after = session.view();
        assertNotNull(after.snapshot(), "snapshot must still be present after advancing");
        assertTrue(after.snapshot().tick() >= 1, "the session must have advanced at least one tick");
    }
}
