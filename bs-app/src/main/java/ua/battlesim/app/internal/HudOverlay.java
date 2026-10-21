package ua.battlesim.app.internal;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

import ua.battlesim.api.Outcome;
import ua.battlesim.api.Side;
import ua.battlesim.gfx.core.ShaderProgram;
import ua.battlesim.ui.Banner;
import ua.battlesim.ui.HealthBar;
import ua.battlesim.ui.HudModel;
import ua.battlesim.ui.HudText;
import ua.battlesim.ui.KeyHint;
import ua.battlesim.ui.NatoMarker;
import ua.battlesim.ui.Rect;
import ua.battlesim.ui.UnitPanel;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

/**
 * The 2-D screen-space HUD renderer: draws the {@link HudModel} (main panel,
 * text, NATO markers, selected-unit panel, round banner, key hints) plus the
 * extra weather lines over the 3-D frame.
 *
 * <p>All positions are client-pixel coordinates with a top-left origin,
 * matching the {@link Rect}s produced by the bs-ui {@code HudRenderer}.
 *
 * <p>Requires a current OpenGL context (3.3+); GPU resources are created on
 * the first draw call. Not thread-safe; call from the render thread.
 */
public final class HudOverlay implements AutoCloseable {

    private static final int FLOATS_PER_VERTEX = 9;   // pos(2) + uv(2) + color(4) + useTex(1)
    private static final int MAX_VERTICES = 16384;
    private static final double OUTLINE_PX = 2.0;

    private static final float[] BG_PANEL = { 0.10f, 0.12f, 0.16f, 0.60f };
    private static final float[] BG_BANNER = { 0.12f, 0.12f, 0.16f, 0.85f };
    private static final float[] MARKER_BLUE = { 0.30f, 0.55f, 1.0f, 1.0f };
    private static final float[] MARKER_RED = { 1.0f, 0.30f, 0.30f, 1.0f };
    private static final float[] MARKER_DESTROYED = { 0.55f, 0.55f, 0.55f, 0.90f };
    private static final float[] MARKER_BLUE_BG = { 0.05f, 0.15f, 0.45f, 0.85f };
    private static final float[] MARKER_RED_BG = { 0.45f, 0.10f, 0.10f, 0.85f };
    private static final float[] COLOR_WHITE = { 1.0f, 1.0f, 1.0f, 1.0f };
    private static final float[] COLOR_DIM = { 0.75f, 0.75f, 0.75f, 1.0f };
    private static final float[] COLOR_HEALTH = { 0.30f, 0.90f, 0.40f, 1.0f };
    private static final float[] COLOR_HEALTH_LOW = { 1.0f, 0.45f, 0.25f, 1.0f };

    private final TextAtlas atlas = new TextAtlas();
    private final ByteBuffer vertexData =
            ByteBuffer.allocateDirect(MAX_VERTICES * FLOATS_PER_VERTEX * 4);

    private ShaderProgram program;
    private int vao;
    private int vbo;
    private int vertexCount;
    private boolean closed;

    /**
     * Draws the HUD model and the extra lines over the current frame.
     *
     * @param width      the viewport width in client pixels, &gt; 0
     * @param height     the viewport height in client pixels, &gt; 0
     * @param model      the HUD model (non-null)
     * @param extraLines extra text lines rendered under the main panel
     *                   (non-null, may be empty)
     */
    public void render(int width, int height, HudModel model, List<String> extraLines) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(extraLines, "extraLines");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("viewport must be > 0: " + width + "x" + height);
        }
        if (closed) {
            throw new IllegalStateException("HudOverlay is closed");
        }
        ensureGpu();
        vertexCount = 0;
        vertexData.clear();   // reset the buffer position to 0 before writing this frame

        drawMainPanel(model, extraLines);
        drawMarkers(model);
        model.selectedUnit().ifPresent(this::drawUnitPanel);
        model.banner().ifPresent(this::drawBanner);
        model.keyHints().forEach(this::drawKeyHint);

        vertexData.flip();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexData);
        program.use();
        atlas.gpu().bind();
        program.setFloat("uWidth", (float) width);
        program.setFloat("uHeight", (float) height);

        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDrawArrays(GL_QUADS, 0, vertexCount);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
        program.unuse();

        // restore the 3-D state for the next frame
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
    }

    private void drawMainPanel(HudModel model, List<String> extraLines) {
        if (model.panelTexts().isEmpty()) {
            return;
        }
        Rect first = model.panelTexts().get(0).rect();
        double bottom = first.bottom();
        double right = first.right();
        for (HudText text : model.panelTexts()) {
            bottom = Math.max(bottom, text.rect().bottom());
            right = Math.max(right, text.rect().right());
        }
        double extraHeight = extraLines.size() * (TextAtlas.CELL + 2.0);
        quad(first.x() - 8, first.y() - 8, (right - first.x()) + 16.0,
                (bottom - first.y()) + extraHeight + 16.0, BG_PANEL);
        for (HudText text : model.panelTexts()) {
            drawText(text.text(), text.rect().x(), text.rect().y(), text.fontSize(), color(text.colorArgb()));
        }
        double y = bottom + 6.0;
        for (String line : extraLines) {
            drawText(line, first.x(), y, 13, COLOR_DIM);
            y += TextAtlas.CELL + 2.0;
        }
    }

    private void drawMarkers(HudModel model) {
        for (NatoMarker marker : model.markers()) {
            float[] border = marker.destroyed() ? MARKER_DESTROYED
                    : (marker.side() == Side.BLUE ? MARKER_BLUE : MARKER_RED);
            float[] fill = marker.destroyed() ? MARKER_DESTROYED
                    : (marker.side() == Side.BLUE ? MARKER_BLUE_BG : MARKER_RED_BG);
            Rect r = marker.rect();
            quad(r.x(), r.y(), r.width(), r.height(), fill);
            outline(r.x(), r.y(), r.width(), r.height(), OUTLINE_PX, border);
        }
    }

    private void drawUnitPanel(UnitPanel panel) {
        Rect r = panel.rect();
        quad(r.x(), r.y(), r.width(), r.height(), BG_PANEL);
        double x = r.x() + 12;
        double y = r.y() + 10;
        drawText(panel.sideLabel(), x, y, 15, COLOR_WHITE);
        y += 22;
        drawText(panel.typeLabel(), x, y, 14, COLOR_WHITE);
        y += 20;
        drawText(panel.coordinates(), x, y, 13, COLOR_DIM);
        y += 18;
        drawText(panel.compassHeading(), x, y, 13, COLOR_DIM);
        y += 18;
        drawText(panel.stateLabel(), x, y, 13, COLOR_WHITE);
        y += 22;
        if (panel.targetLabel() != null && !panel.targetLabel().isEmpty()) {
            drawText(panel.targetLabel(), x, y, 13, COLOR_DIM);
            y += 22;
        }
        HealthBar bar = panel.healthBar();
        double fraction = bar.maxHealth() > 0 ? (double) bar.health() / bar.maxHealth() : 0.0;
        quad(bar.rect().x(), bar.rect().y(), bar.rect().width(), bar.rect().height(), BG_BANNER);
        if (fraction > 0.0) {
            quad(bar.rect().x(), bar.rect().y(), bar.rect().width() * fraction, bar.rect().height(),
                    fraction > 0.4 ? COLOR_HEALTH : COLOR_HEALTH_LOW);
        }
    }

    private void drawBanner(Banner banner) {
        Rect r = banner.rect();
        quad(r.x(), r.y(), r.width(), r.height(), BG_BANNER);
        float[] color = banner.outcome() == Outcome.BLUE_WIN
                ? new float[] { 0.27f, 0.53f, 1.0f, 1.0f }
                : banner.outcome() == Outcome.RED_WIN
                        ? new float[] { 1.0f, 0.27f, 0.27f, 1.0f }
                        : COLOR_WHITE;
        drawTextCentered(banner.text(), r.centerX(), r.centerY(), 24, color);
    }

    private void drawKeyHint(KeyHint hint) {
        Rect r = hint.rect();
        drawText(hint.key(), r.x(), r.y() + 4, 13, COLOR_WHITE);
        double keyWidth = hint.key().codePointCount(0, hint.key().length()) * TextAtlas.CELL * (13.0 / 16.0);
        drawText(hint.description(), r.x() + keyWidth + 6, r.y() + 4, 13, COLOR_DIM);
    }

    private void ensureGpu() {
        if (program != null) {
            return;
        }
        program = new ShaderProgram(OverlayShaders.VERTEX, OverlayShaders.FRAGMENT);
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, MAX_VERTICES * FLOATS_PER_VERTEX * 4, GL_DYNAMIC_DRAW);
        int stride = FLOATS_PER_VERTEX * 4;
        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 8);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(2, 4, GL_FLOAT, false, stride, 16);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 32);
        glEnableVertexAttribArray(3);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    private void quad(double x, double y, double w, double h, float[] color) {
        vertex(x, y, 0.0f, 0.0f, color, 0.0f);
        vertex(x + w, y, 0.0f, 0.0f, color, 0.0f);
        vertex(x, y + h, 0.0f, 0.0f, color, 0.0f);
        vertex(x + w, y, 0.0f, 0.0f, color, 0.0f);
        vertex(x + w, y + h, 0.0f, 0.0f, color, 0.0f);
        vertex(x, y + h, 0.0f, 0.0f, color, 0.0f);
    }

    private void outline(double x, double y, double w, double h, double t, float[] color) {
        quad(x, y, w, t, color);
        quad(x, y + h - t, w, t, color);
        quad(x, y, t, h, color);
        quad(x + w - t, y, t, h, color);
    }

    private void glyphQuad(double x, double y, double size, int index, float[] color) {
        int col = index % TextAtlas.COLUMNS;
        int row = index / TextAtlas.COLUMNS;
        float u0 = col / (float) TextAtlas.COLUMNS;
        float u1 = (col + 1) / (float) TextAtlas.COLUMNS;
        float v0 = row / (float) atlas.rows();
        float v1 = (row + 1) / (float) atlas.rows();
        vertex(x, y, u0, v0, color, 1.0f);
        vertex(x + size, y, u1, v0, color, 1.0f);
        vertex(x, y + size, u0, v1, color, 1.0f);
        vertex(x + size, y, u1, v0, color, 1.0f);
        vertex(x + size, y + size, u1, v1, color, 1.0f);
        vertex(x, y + size, u0, v1, color, 1.0f);
    }

    private void vertex(double x, double y, float u, float v, float[] color, float useTex) {
        if (vertexCount >= MAX_VERTICES) {
            throw new IllegalStateException("HUD vertex budget exhausted");
        }
        vertexData.putFloat((float) x).putFloat((float) y).putFloat(u).putFloat(v)
                .putFloat(color[0]).putFloat(color[1]).putFloat(color[2]).putFloat(color[3])
                .putFloat(useTex);
        vertexCount++;
    }

    private void drawText(String text, double x, double y, int fontSize, float[] color) {
        double size = TextAtlas.CELL * (fontSize / 16.0);
        double penX = x;
        for (int i = 0; i < text.length();) {
            int codePoint = text.codePointAt(i);
            int index = atlas.index(codePoint);
            if (index >= 0) {
                glyphQuad(penX, y, size, index, color);
            } else {
                quad(penX + 2, y + 2, size - 4, size - 4, color);   // missing glyph: a box
            }
            penX += size;
            i += Character.charCount(codePoint);
        }
    }

    private void drawTextCentered(String text, double centerX, double centerY, int fontSize, float[] color) {
        double width = text.codePointCount(0, text.length()) * TextAtlas.CELL * (fontSize / 16.0);
        drawText(text, centerX - width / 2.0, centerY - fontSize / 2.0, fontSize, color);
    }

    private static float[] color(int argb) {
        return new float[] {
            ((argb >> 16) & 0xFF) / 255.0f,
            ((argb >> 8) & 0xFF) / 255.0f,
            (argb & 0xFF) / 255.0f,
            (argb >>> 24) / 255.0f
        };
    }

    /** Releases all GPU resources. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (program != null) {
            program.close();
            program = null;
        }
        atlas.close();
        if (vbo != 0) {
            glDeleteBuffers(vbo);
            vbo = 0;
        }
        if (vao != 0) {
            glDeleteVertexArrays(vao);
            vao = 0;
        }
    }
}