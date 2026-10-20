package ua.battlesim.gfx.fx.internal;

/**
 * GLSL 3.30 shader sources for the effects and the sky.
 *
 * <p>Each {@code *Vertex}/{@code *Fragment} pair is a self-contained program
 * and the attribute locations match the VAO layouts built by
 * {@link ParticleVao}, {@link SolidVao} and {@link SkyVao}:
 *
 * <ul>
 *   <li>particle: loc 0 pos(3), 1 size(2), 2 color(4); the quad corners come
 *       from {@code gl_VertexID} (4 vertices per particle, instanced draw);</li>
 *   <li>solid: loc 0 pos(3), 1 color(4) (tracer quads);</li>
 *   <li>sky: loc 0 ndc(2) on a full-screen triangle.</li>
 * </ul>
 *
 * <p>The sources are exercised visually through the game, not by the unit
 * tests (the tests never create a shader program).
 */
public final class FxShaders {

    private FxShaders() {
    }

    /** Billboard particle vertex shader (instanced, 4 vertices per particle). */
    public static String particleVertex() {
        return """
                #version 330 core
                layout(location = 0) in vec3 aPos;
                layout(location = 1) in vec2 aSize;
                layout(location = 2) in vec4 aColor;
                uniform mat4 uViewProj;
                uniform vec3 uRight;
                uniform vec3 uUp;
                out vec4 vColor;
                void main() {
                    vec2 c;
                    if (gl_VertexID == 0) { c = vec2(-1.0, -1.0); }
                    else if (gl_VertexID == 1) { c = vec2(1.0, -1.0); }
                    else if (gl_VertexID == 2) { c = vec2(1.0, 1.0); }
                    else { c = vec2(-1.0, 1.0); }
                    vec3 world = aPos + (uRight * c.x + uUp * c.y) * aSize;
                    gl_Position = uViewProj * vec4(world, 1.0);
                    vColor = aColor;
                }
                """;
    }

    /** Unlit particle fragment shader. */
    public static String particleFragment() {
        return """
                #version 330 core
                in vec4 vColor;
                out vec4 FragColor;
                void main() {
                    FragColor = vColor;
                }
                """;
    }

    /** Solid (tracer) vertex shader. */
    public static String solidVertex() {
        return """
                #version 330 core
                layout(location = 0) in vec3 aPos;
                layout(location = 1) in vec4 aColor;
                uniform mat4 uViewProj;
                out vec4 vColor;
                void main() {
                    gl_Position = uViewProj * vec4(aPos, 1.0);
                    vColor = aColor;
                }
                """;
    }

    /** Unlit solid fragment shader. */
    public static String solidFragment() {
        return """
                #version 330 core
                in vec4 vColor;
                out vec4 FragColor;
                void main() {
                    FragColor = vColor;
                }
                """;
    }

    /** Sky vertex shader: full-screen triangle. */
    public static String skyVertex() {
        return """
                #version 330 core
                layout(location = 0) in vec2 aPos;
                out vec2 vNdc;
                void main() {
                    vNdc = aPos;
                    gl_Position = vec4(aPos, 0.9999, 1.0);
                }
                """;
    }

    /** Sky fragment shader: gradient, sun, procedural clouds, lightning flash. */
    public static String skyFragment() {
        return """
                #version 330 core
                in vec2 vNdc;
                uniform mat4 uInvViewProj;
                uniform vec3 uEye;
                uniform vec3 uTop;
                uniform vec3 uHorizon;
                uniform vec3 uSunDir;
                uniform vec3 uSunColor;
                uniform vec3 uCloudColor;
                uniform float uCloudAmount;
                uniform float uFlash;
                out vec4 FragColor;
                float hash(vec2 p) {
                    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
                }
                float noise(vec2 p) {
                    vec2 i = floor(p);
                    vec2 f = fract(p);
                    vec2 u = f * f * (3.0 - 2.0 * f);
                    float a = hash(i);
                    float b = hash(i + vec2(1.0, 0.0));
                    float c = hash(i + vec2(0.0, 1.0));
                    float d = hash(i + vec2(1.0, 1.0));
                    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
                }
                void main() {
                    vec4 w = uInvViewProj * vec4(vNdc, 1.0, 1.0);
                    vec3 dir = normalize(w.xyz / w.w - uEye);
                    float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
                    vec3 col = mix(uHorizon, uTop, smoothstep(0.35, 0.75, h));
                    vec3 sd = normalize(uSunDir);
                    float s = max(dot(dir, sd), 0.0);
                    float sunDisc = pow(s, 800.0) * 1.5 + pow(s, 16.0) * 0.25;
                    col += uSunColor * sunDisc * (1.0 - 0.85 * uCloudAmount);
                    vec2 cp = dir.xz / (max(abs(dir.y), 0.25) + 0.35) * 2.2;
                    float n = noise(cp) * 0.65 + noise(cp * 2.7) * 0.35;
                    float mask = smoothstep(0.35, 0.75, n) * uCloudAmount;
                    col = mix(col, uCloudColor, mask);
                    col += vec3(1.0, 0.98, 0.90) * uFlash * 0.9;
                    FragColor = vec4(col, 1.0);
                }
                """;
    }
}