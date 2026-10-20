# bs-gfx-world

World rendering for the Battle Sim project: terrain surface, water, roads,
bridges and instanced trees and rocks, rendered from a `ua.battlesim.api.Terrain`.

## Public API (`ua.battlesim.gfx.world`)

| Class | Description |
|-------|-------------|
| `WorldRenderer` | `WorldRenderer(Terrain)`; `render(OrbitCamera, LightingParams)`; `dispose()`. Builds all geometry on the CPU in the constructor and creates the OpenGL resources lazily on the first render. Implements `AutoCloseable`. |
| `LightingParams` | Immutable lighting / atmosphere / weather-influenced parameters (sun, ambient, fog, time, snow, wet, puddles, wind). |
| `Vec3` | Immutable finite 3-D vector used by `LightingParams`. |

## Internal (`ua.battlesim.gfx.world.internal`)

- **Mesh builders** (pure CPU, GPU-free, unit-testable):
  - `TerrainMeshBuilder` – height-grid surface with per-vertex normals and baked
    attributes (swamp, forest, baked shade).
  - `WaterMeshBuilder` – surface only where `depthAt > 0`, with flow + depth
    attributes for a soft shoreline.
  - `RoadMeshBuilder` – road ribbons draped on the terrain, 5 vertices across.
  - `BridgeMeshBuilder` – deck, two side rails and three pillars per bridge.
  - `PropMeshBuilder` – instanced trees and rocks (fixed base geometry +
    per-instance `x, y, z, scale, rotation, tint, kind`).
- `Vao` – OpenGL VAO/VBO with an arbitrary interleaved attribute layout and
  optional instancing.
- `Shaders` – GLSL 3.30 sources: procedural grass/dirt/rock, snow cover, wet
  darkening, puddles; animated water with flow normals, Fresnel sky reflection,
  sun glints and foam; and fog driven by visibility.
- `DemoTerrain` – a small deterministic `Terrain` used by the demo and tests.
- `DemoMain` – the visual demo.

## Dependencies

- `bs-api` (contracts)
- `bs-gfx-core` (OrbitCamera, ShaderProgram, Window)
- LWJGL 3.3.3 (OpenGL 3.3 core), JOML 1.10.9

## Build & test

```
./gradlew :bs-gfx-world:check
```

The unit tests verify the mesh builders (expected vertex counts and no
NaN/Infinity) and the renderer's CPU geometry without a GPU.

## Demo

```
./gradlew :bs-gfx-world:run
```

Controls: right-drag rotate, left-drag pan, scroll zoom, `SPACE` toggles a
wet/rainy look, `R` resets the camera, `ESC` closes. (macOS requires
`-XstartOnFirstThread`.)
