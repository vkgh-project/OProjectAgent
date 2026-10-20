package ua.battlesim.gfx.world;

import ua.battlesim.api.Terrain;
import ua.battlesim.gfx.core.OrbitCamera;
import ua.battlesim.gfx.core.ShaderProgram;
import ua.battlesim.gfx.world.internal.BridgeMeshBuilder;
import ua.battlesim.gfx.world.internal.BuiltMesh;
import ua.battlesim.gfx.world.internal.PropMeshBuilder;
import ua.battlesim.gfx.world.internal.RoadMeshBuilder;
import ua.battlesim.gfx.world.internal.Shaders;
import ua.battlesim.gfx.world.internal.TerrainMeshBuilder;
import ua.battlesim.gfx.world.internal.Vao;
import ua.battlesim.gfx.world.internal.WaterMeshBuilder;

import java.util.List;
import java.util.Objects;

/**
 * Renders a {@link Terrain} world: terrain surface, water, roads, bridges and
 * instanced trees and rocks.
 *
 * <p>The construction step builds all the geometry on the CPU (it is pure and
 * deterministic and can be inspected without a GPU). The OpenGL resources
 * (VAOs and shader programs) are created lazily on the first {@link #render}
 * call, which requires a current OpenGL context on the render thread.
 *
 * <p>Threading: not thread-safe; drive it from the single render thread and
 * call {@link #dispose} before releasing the context.
 *
 * <p>Implements AutoCloseable for try-with-resources (delegates to
 * {@link #dispose}).
 */
public final class WorldRenderer implements AutoCloseable {

    /** The default terrain grid resolution (cells per side). */
    public static final int DEFAULT_CELLS = 96;

    private final Terrain terrain;
    private final int gridCells;
    private final BuiltMesh terrainGeometry;
    private final BuiltMesh waterGeometry;
    private final BuiltMesh roadGeometry;
    private final BuiltMesh bridgeGeometry;
    private final PropMeshBuilder.PropSet trees;
    private final PropMeshBuilder.PropSet rocks;

    private Vao terrainVao;
    private Vao waterVao;
    private Vao roadVao;
    private Vao bridgeVao;
    private Vao treeVao;
    private Vao rockVao;
    private ShaderProgram terrainShader;
    private ShaderProgram waterShader;
    private ShaderProgram roadShader;
    private ShaderProgram propShader;
    private boolean gpuReady;

    /**
     * @param terrain the world to render (non-null)
     */
    public WorldRenderer(Terrain terrain) {
        this(terrain, DEFAULT_CELLS);
    }

    /**
     * @param terrain   the world to render (non-null)
     * @param gridCells the terrain/water grid resolution (cells per side), {@code >= 1}
     */
    public WorldRenderer(Terrain terrain, int gridCells) {
        Objects.requireNonNull(terrain, "terrain");
        if (gridCells < 1) {
            throw new IllegalArgumentException("gridCells must be >= 1: " + gridCells);
        }
        this.terrain = terrain;
        this.gridCells = gridCells;
        this.terrainGeometry = TerrainMeshBuilder.build(terrain, gridCells);
        this.waterGeometry = WaterMeshBuilder.build(terrain, gridCells);
        this.roadGeometry = RoadMeshBuilder.build(terrain);
        this.bridgeGeometry = BridgeMeshBuilder.build(terrain);
        this.trees = PropMeshBuilder.buildTrees(terrain);
        this.rocks = PropMeshBuilder.buildRocks(terrain);
    }

    // --- Package-private accessors used by the unit tests (no GPU needed). ---

    BuiltMesh terrainGeometry() {
        return terrainGeometry;
    }

    BuiltMesh waterGeometry() {
        return waterGeometry;
    }

    BuiltMesh roadGeometry() {
        return roadGeometry;
    }

    BuiltMesh bridgeGeometry() {
        return bridgeGeometry;
    }

    PropMeshBuilder.PropSet trees() {
        return trees;
    }

    PropMeshBuilder.PropSet rocks() {
        return rocks;
    }

    int gridCells() {
        return gridCells;
    }

    /**
     * Renders the world for one frame.
     *
     * <p>Requires a current OpenGL context. The GPU resources are created on
     * the first call.
     *
     * @param camera   the view/projection camera (non-null)
     * @param lighting the lighting and weather-influenced parameters (non-null)
     */
    public void render(OrbitCamera camera, LightingParams lighting) {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(lighting, "lighting");
        if (!gpuReady) {
            createGpu();
        }
        org.joml.Vector3f camPos = camera.eyePosition();

        // terrain
        terrainShader.use();
        setCommon(terrainShader, camera, lighting, camPos);
        terrainShader.setFloat("uSnow", lighting.snow());
        terrainShader.setFloat("uWet", lighting.wetness());
        terrainShader.setFloat("uPuddles", lighting.puddles());
        terrainVao.draw();

        // roads and bridges (opaque)
        roadShader.use();
        setCommon(roadShader, camera, lighting, camPos);
        roadVao.draw();
        bridgeVao.draw();

        // props: trees (with wind sway) then rocks
        propShader.use();
        setCommon(propShader, camera, lighting, camPos);
        propShader.setFloat("uTime", lighting.timeSeconds());
        propShader.setFloat("uWindSpeed", lighting.windSpeed());
        propShader.setFloat("uWindDir", lighting.windDirection());
        propShader.setFloat("uMaxDist", camera.maxRadius());
        propShader.setFloat("uIsTree", 1.0f);
        treeVao.drawInstanced(trees.instanceCount());
        propShader.setFloat("uIsTree", 0.0f);
        rockVao.drawInstanced(rocks.instanceCount());

        // water (translucent, drawn last)
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_BLEND);
        org.lwjgl.opengl.GL11.glDepthMask(false);
        org.lwjgl.opengl.GL11.glBlendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
                org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA);
        waterShader.use();
        setCommon(waterShader, camera, lighting, camPos);
        waterShader.setFloat("uTime", lighting.timeSeconds());
        waterVao.draw();
        org.lwjgl.opengl.GL11.glDepthMask(true);
        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_BLEND);
        ShaderProgram.unuse();
    }

    private static void setCommon(ShaderProgram shader, OrbitCamera camera,
                                  LightingParams lighting, org.joml.Vector3f camPos) {
        shader.setMat4("uViewProj", camera.viewProjectionMatrix());
        shader.setVec3("uSunDir", toJoml(lighting.sunDirection()));
        shader.setVec3("uSunColor", toJoml(lighting.sunColor()));
        shader.setVec3("uAmbient", toJoml(lighting.ambientColor()));
        shader.setVec3("uFogColor", toJoml(lighting.fogColor()));
        shader.setFloat("uFogDensity", lighting.fogDensity());
        shader.setVec3("uCamPos", camPos);
    }

    private static org.joml.Vector3f toJoml(Vec3 v) {
        return new org.joml.Vector3f(v.x(), v.y(), v.z());
    }

    private void createGpu() {
        terrainShader = new ShaderProgram(Shaders.terrainVertex(), Shaders.terrainFragment());
        waterShader = new ShaderProgram(Shaders.waterVertex(), Shaders.waterFragment());
        roadShader = new ShaderProgram(Shaders.roadVertex(), Shaders.roadFragment());
        propShader = new ShaderProgram(Shaders.propVertex(), Shaders.propFragment());

        List<Vao.Attribute> terrainAttrs = List.of(
                new Vao.Attribute(0, 3), new Vao.Attribute(1, 3),
                new Vao.Attribute(2, 1), new Vao.Attribute(3, 1), new Vao.Attribute(4, 1));
        List<Vao.Attribute> flatAttrs = List.of(new Vao.Attribute(0, 3), new Vao.Attribute(1, 3));

        terrainVao = new Vao(terrainGeometry.vertices(), terrainGeometry.stride(), terrainAttrs);
        waterVao = new Vao(waterGeometry.vertices(), waterGeometry.stride(), flatAttrs);
        roadVao = new Vao(roadGeometry.vertices(), roadGeometry.stride(), flatAttrs);
        bridgeVao = new Vao(bridgeGeometry.vertices(), bridgeGeometry.stride(), flatAttrs);
        List<Vao.Attribute> propInstAttrs = List.of(
                new Vao.Attribute(PropMeshBuilder.INSTANCE_LOCATION, 3),
                new Vao.Attribute(PropMeshBuilder.INSTANCE_PARAMS_LOCATION, 4));
        treeVao = new Vao(trees.baseVertices(), trees.baseStride(), flatAttrs,
                trees.instanceData(), PropMeshBuilder.INSTANCE_STRIDE, propInstAttrs);
        rockVao = new Vao(rocks.baseVertices(), rocks.baseStride(), flatAttrs,
                rocks.instanceData(), PropMeshBuilder.INSTANCE_STRIDE, propInstAttrs);
        gpuReady = true;
    }

    /**
     * Releases all GPU resources.
     *
     * <p>Safe to call even when {@link #render} was never called (and thus the
     * GPU resources were never created).
     */
    public void dispose() {
        closeVao(terrainVao);
        terrainVao = null;
        closeVao(waterVao);
        waterVao = null;
        closeVao(roadVao);
        roadVao = null;
        closeVao(bridgeVao);
        bridgeVao = null;
        closeVao(treeVao);
        treeVao = null;
        closeVao(rockVao);
        rockVao = null;
        closeShader(terrainShader);
        terrainShader = null;
        closeShader(waterShader);
        waterShader = null;
        closeShader(roadShader);
        roadShader = null;
        closeShader(propShader);
        propShader = null;
        gpuReady = false;
    }

    private static void closeVao(Vao vao) {
        if (vao != null) {
            vao.close();
        }
    }

    private static void closeShader(ShaderProgram shader) {
        if (shader != null) {
            shader.close();
        }
    }

    @Override
    public void close() {
        dispose();
    }
}