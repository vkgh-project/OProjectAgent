package ua.battlesim.app.internal;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import ua.battlesim.gfx.core.Texture;

import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_RGBA;

/**
 * A 16x16 monospace glyph atlas (ASCII 32..126 plus Cyrillic 1025..1103)
 * rendered once from the JVM's built-in font.
 *
 * <p>The atlas is used by {@link HudOverlay} for presentation only — the
 * rendered pixels never feed back into the simulation state, so the system
 * font (rather than a bundled asset) is acceptable here.
 *
 * <p>Requires a current OpenGL context for {@link #gpu()}; CPU-side data is
 * plain Java.
 */
final class TextAtlas {

    /** Pixel size of one glyph cell (square). */
    static final int CELL = 16;

    /** Cells per atlas row. */
    static final int COLUMNS = 16;

    private static final int LATIN_FIRST = 32;
    private static final int LATIN_LAST = 126;
    private static final int CYRILLIC_FIRST = 1025;
    private static final int CYRILLIC_LAST = 1103;

    private final int latinCount = LATIN_LAST - LATIN_FIRST + 1;
    private final int glyphCount = latinCount + (CYRILLIC_LAST - CYRILLIC_FIRST + 1);
    private final int rows = (glyphCount + COLUMNS - 1) / COLUMNS;
    private final int atlasWidth = COLUMNS * CELL;
    private final int atlasHeight = rows * CELL;

    private Texture texture;

    /** @return the atlas width in pixels */
    int width() {
        return atlasWidth;
    }

    /** @return the atlas height in pixels */
    int height() {
        return atlasHeight;
    }

    /** @return the number of atlas rows */
    int rows() {
        return rows;
    }

    /**
     * @param codePoint the character to look up
     * @return the cell index (row-major, top-left origin) or {@code -1} when
     *         the character is not in the atlas
     */
    int index(int codePoint) {
        if (codePoint >= LATIN_FIRST && codePoint <= LATIN_LAST) {
            return codePoint - LATIN_FIRST;
        }
        if (codePoint >= CYRILLIC_FIRST && codePoint <= CYRILLIC_LAST) {
            return latinCount + (codePoint - CYRILLIC_FIRST);
        }
        return -1;
    }

    /**
     * @return the GPU texture, created on first use
     */
    Texture gpu() {
        if (texture == null) {
            texture = new Texture(atlasWidth, atlasHeight, GL_RGBA, pixels());
            texture.setFiltering(GL_NEAREST, GL_NEAREST);
        }
        return texture;
    }

    /** Releases the GPU texture. */
    void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }

    private byte[] pixels() {
        BufferedImage image = new BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            Font font = new Font(Font.MONOSPACED, Font.PLAIN, 11);
            g.setFont(font);
            int baseline = g.getFontMetrics().getAscent() - 2;
            for (int i = 0; i < glyphCount; i++) {
                int col = i % COLUMNS;
                int row = i / COLUMNS;
                g.drawString(String.valueOf((char) codePoint(i)), col * CELL + 1, row * CELL + baseline);
            }
        } finally {
            g.dispose();
        }
        int w = atlasWidth;
        int h = atlasHeight;
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        byte[] out = new byte[w * h * 4];
        for (int row = 0; row < h; row++) {
            int imageRow = h - 1 - row;   // flip rows: GL v=0 must be the top image row
            for (int col = 0; col < w; col++) {
                int pixel = argb[imageRow * w + col];
                int p = (row * w + col) * 4;
                out[p + 0] = (byte) ((pixel >> 16) & 0xFF);
                out[p + 1] = (byte) ((pixel >> 8) & 0xFF);
                out[p + 2] = (byte) (pixel & 0xFF);
                out[p + 3] = (byte) (pixel >>> 24);
            }
        }
        return out;
    }

    private int codePoint(int i) {
        if (i < latinCount) {
            return LATIN_FIRST + i;
        }
        return CYRILLIC_FIRST + (i - latinCount);
    }
}