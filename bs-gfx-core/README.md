# bs-gfx-core

Core graphics infrastructure for the Battle Sim project.

## Public API

| Class | Description |
|-------|-------------|
| `ua.battlesim.gfx.core.Window` | GLFW window with OpenGL 3.3 core, 4x MSAA, HiDPI, fullscreen toggle (F11) |
| `ua.battlesim.gfx.core.ShaderProgram` | Shader compilation, uniform cache, clear error messages |
| `ua.battlesim.gfx.core.Mesh` | GPU mesh (VAO/VBO) for triangle drawing |
| `ua.battlesim.gfx.core.InstancedMesh` | Instanced GPU mesh with per-instance data |
| `ua.battlesim.gfx.core.Texture` | 2D GPU texture with wrap/filter control |
| `ua.battlesim.gfx.core.OrbitCamera` | Orbit camera: pan, rotate, zoom, follow, clamps, view-projection matrices |
| `ua.battlesim.gfx.core.InputRouter` | Routes raw input events to UiCommand records |
| `ua.battlesim.gfx.core.UiCommand` | Sealed interface: TogglePause, SpeedUp, SpeedDown, FocusSelected, ResetCamera, ToggleFullscreen, Click, DragPan, DragRotate, Zoom |

## Internal

- `ua.battlesim.gfx.core.internal.DemoMain` - rotating triangle demo

## Dependencies

- `bs-api` (contracts)
- LWJGL 3.3.3 (GLFW, OpenGL)
- JOML 1.10.9 (matrix/vector math)

## Build

```
./gradlew :bs-gfx-core:check
```

## Demo

```
./gradlew :bs-gfx-core:run
```

Or directly (macOS requires `-XstartOnFirstThread`):
```
java -cp bs-gfx-core/build/libs/bs-gfx-core-0.1.0.jar:libs/* ua.battlesim.gfx.core.internal.DemoMain
```

## Tests

- `OrbitCameraTest` - camera maths (pan, rotate, zoom, follow, clamps, matrices, determinism)
- `UiCommandTest` - record validation
- `InputRouterTest` - key/mouse/scroll routing, drag threshold, click vs drag
