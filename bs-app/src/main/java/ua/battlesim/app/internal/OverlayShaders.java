package ua.battlesim.app.internal;

/**
 * The GLSL 3.30 shader sources of the {@link HudOverlay} 2-D screen-space
 * renderer: a per-vertex colour (with optional glyph-atlas alpha) mapped from
 * client pixels to clip space.
 */
final class OverlayShaders {

    private OverlayShaders() {
    }

    /** Vertex source: pos in client pixels, top-left origin. */
    static final String VERTEX = """
            #version 330 core
            layout(location = 0) in vec2 aPos;
            layout(location = 1) in vec2 aUv;
            layout(location = 2) in vec4 aColor;
            layout(location = 3) in float aUseTex;

            uniform float uWidth;
            uniform float uHeight;

            out vec2 vUv;
            out vec4 vColor;
            out float vUseTex;

            void main() {
                vec2 clip = aPos / vec2(uWidth, uHeight) * 2.0 - 1.0;
                gl_Position = vec4(clip.x, -clip.y, 0.0, 1.0);
                vUv = aUv;
                vColor = aColor;
                vUseTex = aUseTex;
            }
            """;

    /** Fragment source: solid colour, or colour masked by the atlas alpha. */
    static final String FRAGMENT = """
            #version 330 core
            in vec2 vUv;
            in vec4 vColor;
            in float vUseTex;

            uniform sampler2D uTex;

            out vec4 frag;

            void main() {
                float alpha = mix(1.0, texture(uTex, vUv).a, vUseTex);
                frag = vec4(vColor.rgb, vColor.a * alpha);
            }
            """;
}