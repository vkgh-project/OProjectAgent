package ua.battlesim.gfx.units.internal;

/**
 * GLSL 3.30 shader sources for the unit renderer.
 *
 * <p>Attributes: {@code 0} position (3), {@code 1} normal (3), {@code 2}
 * colour (4). The fragment shader lights each vertex with a sun term plus a
 * strong ambient fill (clamped so models are never fully dark) and applies
 * distance fog driven by the round weather.
 *
 * <p>The shaders are exercised visually through the demo, not by the unit
 * tests (which run without a GPU).
 */
public final class Shaders {

    private Shaders() {
    }

    /** Unit vertex shader: passes through position, normal and colour. */
    public static String vertex() {
        return """
                #version 330 core
                layout(location = 0) in vec3 aPos;
                layout(location = 1) in vec3 aNormal;
                layout(location = 2) in vec4 aColor;
                uniform mat4 uViewProj;
                out vec3 vNormal;
                out vec3 vWorldPos;
                out vec4 vColor;
                void main() {
                    vNormal = aNormal;
                    vWorldPos = aPos;
                    vColor = aColor;
                    gl_Position = uViewProj * vec4(aPos, 1.0);
                }
                """;
    }

    /** Unit fragment shader: sun + strong ambient fill, distance fog. */
    public static String fragment() {
        return """
                #version 330 core
                in vec3 vNormal;
                in vec3 vWorldPos;
                in vec4 vColor;
                uniform vec3 uSunDir;
                uniform vec3 uSunColor;
                uniform vec3 uAmbient;
                uniform vec3 uCamPos;
                uniform vec3 uFogColor;
                uniform float uFogDensity;
                out vec4 FragColor;
                void main() {
                    vec3 N = normalize(vNormal);
                    float diff = max(dot(N, normalize(uSunDir)), 0.0);
                    vec3 lit = vColor.rgb * (uAmbient + uSunColor * diff);
                    // Strong ambient fill so models are never fully dark.
                    lit = max(lit, vColor.rgb * 0.30);
                    float dist = distance(uCamPos, vWorldPos);
                    float fog = 1.0 - exp(-uFogDensity * uFogDensity * dist * dist);
                    lit = mix(lit, uFogColor, clamp(fog, 0.0, 1.0));
                    FragColor = vec4(lit, vColor.a);
                }
                """;
    }
}
