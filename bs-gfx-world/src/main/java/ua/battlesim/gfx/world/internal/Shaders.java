package ua.battlesim.gfx.world.internal;

/**
 * GLSL 3.30 shader sources for the world.
 *
 * <p>Each {@code *Vertex}/{@code *Fragment} pair is a self-contained program.
 * The attribute locations match the {@link Vao} layouts built by the mesh
 * builders:
 *
 * <ul>
 *   <li>terrain: loc 0 pos(3), 1 normal(3), 2 swamp(1), 3 forest(1), 4 shade(1)</li>
 *   <li>water: loc 0 pos(3), 1 flow/depth(3)</li>
 *   <li>road/bridge: loc 0 pos(3), 1 normal(3)</li>
 *   <li>props: loc 0 pos(3), 1 normal(3), 2 instance(7)</li>
 * </ul>
 *
 * <p>The shaders implement the requested features: procedural grass/dirt/rock,
 * snow cover, wet darkening, puddles; animated water with flow normals,
 * Fresnel sky reflection, sun glints and foam; and fog driven by visibility.
 * They are exercised visually through the demo, not by the unit tests.
 */
public final class Shaders {

    private Shaders() {
    }

    /** Terrain vertex shader. */
    public static String terrainVertex() {
        return """
                #version 330 core
                layout(location = 0) in vec3 aPos;
                layout(location = 1) in vec3 aNormal;
                layout(location = 2) in float aSwamp;
                layout(location = 3) in float aForest;
                layout(location = 4) in float aShade;
                uniform mat4 uViewProj;
                out vec3 vNormal;
                out vec3 vWorldPos;
                out float vSwamp;
                out float vForest;
                out float vShade;
                void main() {
                    vNormal = aNormal;
                    vWorldPos = aPos;
                    vSwamp = aSwamp;
                    vForest = aForest;
                    vShade = aShade;
                    gl_Position = uViewProj * vec4(aPos, 1.0);
                }
                """;
    }

    /** Terrain fragment shader: grass/dirt/rock, snow, wet, puddles, fog. */
    public static String terrainFragment() {
        return """
                #version 330 core
                in vec3 vNormal;
                in vec3 vWorldPos;
                in float vSwamp;
                in float vForest;
                in float vShade;
                uniform vec3 uSunDir;
                uniform vec3 uSunColor;
                uniform vec3 uAmbient;
                uniform vec3 uFogColor;
                uniform float uFogDensity;
                uniform vec3 uCamPos;
                uniform float uSnow;
                uniform float uWet;
                uniform float uPuddles;
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
                    vec3 N = normalize(vNormal);
                    float slope = 1.0 - N.y;
                    float n = noise(vWorldPos.xz * 0.15);
                    vec3 grass = vec3(0.22, 0.42, 0.18);
                    vec3 dirt  = vec3(0.45, 0.34, 0.22);
                    vec3 rock  = vec3(0.50, 0.50, 0.52);
                    vec3 col = mix(grass, dirt, smoothstep(0.15, 0.40, slope + vSwamp * 0.30));
                    col = mix(col, rock, smoothstep(0.45, 0.75, slope));
                    col = mix(col, grass * 0.60, vForest * 0.60);
                    col = mix(col, vec3(0.30, 0.28, 0.22), vSwamp * 0.50);
                    float pud = smoothstep(0.5, 1.0, uPuddles)
                            * step(0.5, hash(floor(vWorldPos.xz * 0.2)))
                            * (1.0 - N.y) * (1.0 - vSwamp);
                    col = mix(col, col * 0.5 + vec3(0.05), pud);
                    col = col * (1.0 - 0.35 * uWet);
                    vec3 snow = vec3(0.92, 0.94, 0.98);
                    col = mix(col, snow, uSnow * smoothstep(0.5, 0.75, N.y) * (1.0 - vSwamp) * n);
                    float diff = max(dot(N, normalize(uSunDir)), 0.0);
                    vec3 lighting = uAmbient + uSunColor * diff * vShade;
                    vec3 color = col * lighting;
                    float dist = distance(uCamPos, vWorldPos);
                    float fog = 1.0 - exp(-uFogDensity * uFogDensity * dist * dist);
                    color = mix(color, uFogColor, clamp(fog, 0.0, 1.0));
                    FragColor = vec4(color, 1.0);
                }
                """;
    }

    /** Water vertex shader. */
    public static String waterVertex() {
        return """
                #version 330 core
                layout(location = 0) in vec3 aPos;
                layout(location = 1) in vec3 aFlow;
                uniform mat4 uViewProj;
                out vec3 vWorldPos;
                out vec2 vFlow;
                out float vDepth;
                void main() {
                    vWorldPos = aPos;
                    vFlow = vec2(aFlow.x, aFlow.y);
                    vDepth = aFlow.z;
                    gl_Position = uViewProj * vec4(aPos, 1.0);
                }
                """;
    }

    /** Water fragment shader: animated normals, Fresnel, glints, foam, fog. */
    public static String waterFragment() {
        return """
                #version 330 core
                in vec3 vWorldPos;
                in vec2 vFlow;
                in float vDepth;
                uniform vec3 uSunDir;
                uniform vec3 uCamPos;
                uniform float uTime;
                uniform float uFogDensity;
                uniform vec3 uFogColor;
                out vec4 FragColor;
                float hash(vec2 p) {
                    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
                }
                void main() {
                    vec2 uv = vWorldPos.xz * 0.08 + vFlow * 0.5;
                    float t = uTime * 0.5;
                    float n1 = hash(floor(uv) + t);
                    float n2 = hash(floor(uv * 1.7) - t);
                    vec3 N = normalize(vec3((n1 - 0.5) * 0.3, 1.0, (n2 - 0.5) * 0.3));
                    vec3 V = normalize(uCamPos - vWorldPos);
                    vec3 R = reflect(-V, N);
                    float fresnel = pow(1.0 - max(dot(V, N), 0.0), 3.0);
                    float sunGlint = pow(max(dot(R, normalize(uSunDir)), 0.0), 80.0);
                    float foam = 1.0 - smoothstep(0.0, 1.5, vDepth);
                    vec3 deep = vec3(0.05, 0.18, 0.25);
                    vec3 shallow = vec3(0.15, 0.40, 0.50);
                    vec3 col = mix(deep, shallow, smoothstep(0.0, 4.0, vDepth));
                    col = mix(col, vec3(0.90, 0.95, 1.0), sunGlint * 0.6 + foam * 0.4);
                    vec3 sky = mix(vec3(0.5, 0.7, 0.9), vec3(0.9, 0.95, 1.0), R.y * 0.5 + 0.5);
                    col = mix(col, sky, fresnel * 0.5);
                    float alpha = smoothstep(0.0, 0.6, vDepth) * 0.85 + 0.1;
                    float dist = distance(uCamPos, vWorldPos);
                    float fog = 1.0 - exp(-uFogDensity * uFogDensity * dist * dist);
                    col = mix(col, uFogColor, clamp(fog, 0.0, 1.0));
                    FragColor = vec4(col, clamp(alpha, 0.0, 1.0));
                }
                """;
    }

    /** Road/bridge vertex shader. */
    public static String roadVertex() {
        return """
                #version 330 core
                layout(location = 0) in vec3 aPos;
                layout(location = 1) in vec3 aNormal;
                uniform mat4 uViewProj;
                out vec3 vNormal;
                out vec3 vWorldPos;
                void main() {
                    vNormal = aNormal;
                    vWorldPos = aPos;
                    gl_Position = uViewProj * vec4(aPos, 1.0);
                }
                """;
    }

    /** Road/bridge fragment shader: lit asphalt with fog. */
    public static String roadFragment() {
        return """
                #version 330 core
                in vec3 vNormal;
                in vec3 vWorldPos;
                uniform vec3 uSunDir;
                uniform vec3 uSunColor;
                uniform vec3 uAmbient;
                uniform vec3 uFogColor;
                uniform float uFogDensity;
                uniform vec3 uCamPos;
                out vec4 FragColor;
                void main() {
                    vec3 N = normalize(vNormal);
                    float diff = max(dot(N, normalize(uSunDir)), 0.0);
                    vec3 asphalt = vec3(0.18, 0.18, 0.20);
                    vec3 col = asphalt * (uAmbient + uSunColor * diff);
                    float dist = distance(uCamPos, vWorldPos);
                    float fog = 1.0 - exp(-uFogDensity * uFogDensity * dist * dist);
                    col = mix(col, uFogColor, clamp(fog, 0.0, 1.0));
                    FragColor = vec4(col, 1.0);
                }
                """;
    }

    /** Prop (tree/rock) vertex shader: instance transform, wind sway, culling. */
    public static String propVertex() {
        return """
                #version 330 core
                layout(location = 0) in vec3 aPos;
                layout(location = 1) in vec3 aNormal;
                layout(location = 2) in vec7 aInstance;
                uniform mat4 uViewProj;
                uniform vec3 uCamPos;
                uniform float uTime;
                uniform float uWindSpeed;
                uniform float uWindDir;
                uniform float uMaxDist;
                uniform float uIsTree;
                out vec3 vNormal;
                out vec3 vWorldPos;
                out float vTint;
                out float vIsTree;
                void main() {
                    float ix = aInstance.x;
                    float iy = aInstance.y;
                    float iz = aInstance.z;
                    float scale = aInstance.w;
                    float rot = aInstance.u;
                    float tint = aInstance.v;
                    float c = cos(rot);
                    float s = sin(rot);
                    vec3 local = vec3(c * aPos.x - s * aPos.z, aPos.y, s * aPos.x + c * aPos.z)
                            * scale + vec3(ix, iy, iz);
                    if (uIsTree > 0.5) {
                        float sway = sin(uTime * uWindSpeed + local.x * 0.2 + local.z * 0.2);
                        float hgt = max(local.y - (iy + 0.4 * scale), 0.0);
                        local.xz += (vec2(cos(uWindDir), sin(uWindDir)) * sway * hgt * 0.05);
                    }
                    if (distance(uCamPos, local) > uMaxDist) {
                        local = vec3(1.0e5, 1.0e5, 1.0e5);
                    }
                    vNormal = aNormal;
                    vWorldPos = local;
                    vTint = tint;
                    vIsTree = uIsTree;
                    gl_Position = uViewProj * vec4(local, 1.0);
                }
                """;
    }

    /** Prop fragment shader: trunk/leaf or rock, tint, snow, fog. */
    public static String propFragment() {
        return """
                #version 330 core
                in vec3 vNormal;
                in vec3 vWorldPos;
                in float vTint;
                in float vIsTree;
                uniform vec3 uSunDir;
                uniform vec3 uSunColor;
                uniform vec3 uAmbient;
                uniform vec3 uFogColor;
                uniform float uFogDensity;
                uniform vec3 uCamPos;
                uniform float uSnow;
                out vec4 FragColor;
                void main() {
                    vec3 N = normalize(vNormal);
                    float diff = max(dot(N, normalize(uSunDir)), 0.0);
                    vec3 col;
                    if (vIsTree > 0.5) {
                        vec3 trunk = vec3(0.35, 0.25, 0.15);
                        vec3 leaf = mix(vec3(0.10, 0.35, 0.10), vec3(0.20, 0.50, 0.15), vTint);
                        col = mix(trunk, leaf, step(0.4, N.y));
                    } else {
                        col = vec3(0.45, 0.45, 0.47) * (0.8 + 0.2 * vTint);
                    }
                    col = col * (uAmbient + uSunColor * diff);
                    col = mix(col, vec3(0.9), uSnow * step(0.5, N.y));
                    float dist = distance(uCamPos, vWorldPos);
                    float fog = 1.0 - exp(-uFogDensity * uFogDensity * dist * dist);
                    col = mix(col, uFogColor, clamp(fog, 0.0, 1.0));
                    FragColor = vec4(col, 1.0);
                }
                """;
    }
}