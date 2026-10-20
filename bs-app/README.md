# bs-app

The composition root of Battle Sim (layer 4). It wires every lower module into
a runnable application — headless or windowed — and owns the main loop. It adds
**no new simulation logic**: all rules come from the modules it composes.

**Public API** (`ua.battlesim.app`):

- `Main` — the entry point (`mainClass` for the Gradle `application` plugin).

**Internal** (`ua.battlesim.app.internal`):

- `ConfigParser` / `AppConfig` — command-line and `battle-sim.properties`
  parsing into the resolved round configuration.
- `AppWiring` — the composition root: the terrain pipeline behind
  `TerrainService`, `StandardCombatSystem`, `StandardMovementSystem`,
  `StandardScenarioFactory`, `DefaultEngineFactory`, `DefaultSession` and the
  presentation-layer `SimpleWeatherModel`.
- `AppTerrainService` / `AppTerrain` — bs-terrain + bs-features + bs-nav glued
  into one immutable bs-api `Terrain`.
- `HeadlessGame` — plays N full rounds (default 3) through the real core
  modules, replays them and verifies determinism by comparing outcomes and
  state hashes.
- `WindowedGame` — the windowed main loop: poll input → `InputRouter` →
  `UiCommand` → session/camera calls → `session.advance(dt)` → render sky,
  world, units, effects and the HUD.
- `HudOverlay` / `TextAtlas` / `OverlayShaders` — the 2-D screen-space HUD
  renderer (panels, text, NATO markers, selected-unit panel, banner, key
  hints, weather lines) on top of the bs-ui `HudModel`.

## Wiring

| Contract (bs-api)      | Implementation (module)                        |
| ---------------------- | ---------------------------------------------- |
| `TerrainService`       | `AppTerrainService` (bs-terrain + bs-features + bs-nav) |
| `CombatSystem`         | `StandardCombatSystem` (bs-combat)             |
| `MovementSystem`       | `StandardMovementSystem` (bs-movement)         |
| `ScenarioFactory`      | `StandardScenarioFactory` (bs-scenario)        |
| `EngineFactory`        | `DefaultEngineFactory` (bs-engine)             |
| `Session`              | `DefaultSession` (bs-session)                  |
| `WeatherModel` (HUD)   | `SimpleWeatherModel` (bs-weather)              |

Same-layer rule: bs-app and bs-tools are both layer-4 composition roots and
therefore **never depend on each other**; they share only the bs-api
contracts. This is enforced by `LayeringTest` (ArchUnit).

## Usage

```
usage: battle-sim-app [--seed N] [--windowed] [--config FILE]
```

- `--seed N` — the deterministic master seed (default `1`).
- `--windowed` — open the OpenGL window and run the render loop; without it
  the app plays its headless path (3 rounds + determinism check) and exits.
- `--config FILE` — a properties file; when absent, `battle-sim.properties`
  in the working directory is read if present, otherwise the standard
  defaults apply.

Properties (all optional):

```properties
mapSize=2000          # map side length in metres (> 0)
weatherEnabled=false  # weather on/off
unitsPerSide=0        # unit scale: exactly N per side (0 = standard 1..20 range)
maxRoundTicks=18000   # round length bound in ticks (>= 1)
```

Exit codes: `0` success, `2` headless determinism check failed, `64` invalid
input.

## Build

- `./gradlew :bs-app:check` — compile, tests (layering, headless smoke x3
  rounds, config parsing, wiring) and `check`.
- `./gradlew :bs-app:run` — headless 3-round run (add
  `--args="--seed 42 --windowed"` for the windowed loop).

The `application` plugin sets `--enable-native-access=ALL-UNNAMED` for the
LWJGL natives on every OS and `-XstartOnFirstThread` on macOS, and pulls the
LWJGL natives per OS (windows / linux / macos) at runtime.

## Tests

- `LayeringTest` — ArchUnit: dependencies point downward only; same-layer
  modules never depend on each other; bs-app never depends on bs-tools.
- `HeadlessSmokeTest` — the headless path plays 3 full rounds for one seed and
  the replay yields identical outcomes and state hashes; also drives
  `Main.run` end-to-end with a config file.
- `ConfigParserTest` — CLI flags, defaults, `battle-sim.properties` keys and
  rejection of invalid input.
- `AppWiringTest` — the wired graph is functional: complete terrain, both
  sides present, the engine advances, the weather model reports conditions.
