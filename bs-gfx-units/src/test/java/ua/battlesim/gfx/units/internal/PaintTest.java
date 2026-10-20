package ua.battlesim.gfx.units.internal;

import ua.battlesim.api.Side;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the paint resolution: side colours, the brighter vest, charred
 * darkening and the translucent shadow.
 */
class PaintTest {

    @Test
    void side_colours() {
        float[] b = Paint.side(Side.BLUE);
        float[] r = Paint.side(Side.RED);
        assertTrue(b[2] > b[0] && b[2] > b[1], "blue should be bluish");
        assertTrue(r[0] > r[1] && r[0] > r[2], "red should be reddish");
    }

    @Test
    void sidePaint_appliedToHullAndHead() {
        float[] side = Paint.side(Side.BLUE);
        float[] hull = Paint.color(Role.HULL, side, false, false);
        float[] head = Paint.color(Role.HEAD, side, false, false);
        assertArrayEquals(side, new float[] { hull[0], hull[1], hull[2] }, 1e-6f);
        assertArrayEquals(side, new float[] { head[0], head[1], head[2] }, 1e-6f);
    }

    @Test
    void vest_isBrighterThanHull() {
        float[] side = Paint.side(Side.BLUE);
        float[] hull = Paint.color(Role.HULL, side, false, false);
        float[] vest = Paint.color(Role.VEST, side, false, false);
        assertTrue(vest[0] >= hull[0] && vest[1] >= hull[1] && vest[2] >= hull[2]);
    }

    @Test
    void destroyed_darkens() {
        float[] side = Paint.side(Side.BLUE);
        float[] normal = Paint.color(Role.HULL, side, false, false);
        float[] charred = Paint.color(Role.HULL, side, true, false);
        assertTrue(charred[0] < normal[0] && charred[1] < normal[1] && charred[2] < normal[2]);
    }

    @Test
    void selected_highlights() {
        float[] side = Paint.side(Side.BLUE);
        float[] normal = Paint.color(Role.HULL, side, false, false);
        float[] selected = Paint.color(Role.HULL, side, false, true);
        assertTrue(selected[0] >= normal[0] && selected[1] >= normal[1] && selected[2] >= normal[2]);
    }

    @Test
    void shadow_isTranslucentBlack() {
        float[] side = Paint.side(Side.BLUE);
        float[] sh = Paint.color(Role.SHADOW, side, false, false);
        assertTrue(sh[3] > 0.0f && sh[3] < 1.0f, "shadow alpha must be in (0,1)");
        assertTrue(sh[0] == 0.0f && sh[1] == 0.0f && sh[2] == 0.0f);
    }
}
