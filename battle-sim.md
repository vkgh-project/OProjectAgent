# Battle Sim: Java architecture and AI prompt pack

Goal: split the simulator into small, almost independent parts, build them one by one with an AI assistant (one prompt per part), then compose them into one application.

## 1. Principles

- **Contract first.** One tiny module (bs-api) holds every shared interface and data record. All other modules depend on bs-api and almost nothing else, so each can be built and tested against fakes.
- **Headless core, thin renderer.** The simulation never imports graphics. Graphics only read an immutable snapshot.
- **Deterministic by design.** The same master seed gives the same terrain, units, weather and battle results.
- **Small modules** (roughly 300-900 lines) so one prompt fits one module and the result can be reviewed in one sitting.
- **Definition of done** for every module: tests with fakes of its neighbours, and `./gradlew :module:check` passes.
- **Use the working monolith as an oracle.** Paste the matching class of the existing battle-sim3d project into the prompt as reference behaviour and compare results per seed.

## 2. Layers and dependency direction

```
Layer 4  bs-app (composition root)        bs-tools (headless runner)
Layer 3  bs-ui   bs-gfx-world   bs-gfx-units   bs-gfx-fx   -> bs-gfx-core
Layer 2  bs-session   bs-engine   bs-combat   bs-movement   bs-weather   bs-scenario
Layer 1  bs-terrain   bs-features   bs-nav
Layer 0  bs-math      bs-api (contracts, no dependencies)
```

Rule: dependencies point downward only. Modules of the same layer never depend on each other; they talk through interfaces from bs-api and bs-app injects the implementations (for example the engine receives a CombatSystem, a MovementSystem and a WeatherModel, it does not import the modules that implement them). Enforce this with ArchUnit tests.

## 3. Module catalogue

| # | Module | Responsibility | Depends on |
| --- | --- | --- | --- |
| 0 | bs-api | contracts: interfaces, records, enums, unit table | none |
| 1 | bs-math | deterministic RNG, noise, polyline and grid helpers | api |
| 2 | bs-terrain | heightmap, ravines, rivers, lakes, swamps, baked shadows | api, math |
| 3 | bs-features | roads, bridges, trees, rocks | api, math |
| 4 | bs-nav | navigation grid, line of sight, A\* path planner | api, math |
| 5 | bs-weather | weather state machine and its effects | api, math |
| 6 | bs-combat | targeting, damage table, simultaneous resolution | api |
| 7 | bs-movement | steering, collisions, target choice, stuck recovery | api |
| 8 | bs-scenario | round factory: seeds, unit counts, placement | api, math |
| 9 | bs-engine | one fixed tick, end-of-round rules, snapshots, events | api |
| 10 | bs-session | round loop, pause, speed, prefetch, statistics | api |
| 11 | bs-tools | headless runner, determinism and speed checks | all core |
| 12 | bs-gfx-core | window, GL context, shaders, camera, input | api |
| 13 | bs-gfx-world | terrain, water, roads, bridges, vegetation rendering | api, gfx-core |
| 14 | bs-gfx-units | unit models and animation | api, gfx-core |
| 15 | bs-gfx-fx | particles, tracers, sky, lighting, rain and snow | api, gfx-core |
| 16 | bs-ui | HUD, NATO symbols, picking | api, gfx-core |
| 17 | bs-app | wiring, configuration, Gradle application | everything |

## 4. Shared contracts (module bs-api)

```java
package ua.battlesim.api;

public record Vec2(double x, double z) {}        // metres; x = east, z = south
public enum Side { BLUE, RED }
public enum UnitType { TANK, INFANTRY, APC_INFANTRY }
public enum UnitState { READY, MOVING, ATTACKING, WAITING, DESTROYED }
public enum Outcome { BLUE_WIN, RED_WIN, DRAW, DRAW_TIME }

public record SimConfig(double mapSize, double stepSeconds, int attackIntervalTicks,
        int maxRoundTicks, int readyTicks, int minUnits, int maxUnits, boolean weatherEnabled) {
    public static SimConfig defaults() { ... }   // 2000 m, 0.1 s, 20, 18000, 20, 1, 20, false
}

// ---- world
public interface HeightField { double size(); double heightAt(double x, double z); }
public interface WaterField { double depthAt(double x, double z); Vec2 flowAt(double x, double z); double swampAt(double x, double z); }
public interface NavGrid {
    int cells(); double cellSize();
    boolean passable(int i, int j); int component(int i, int j); int mainComponent();
    boolean walkable(double x, double z, double radius); boolean isRoad(double x, double z);
}
public interface LineOfSight { boolean visible(Vec2 from, double eyeFrom, Vec2 to, double eyeTo); }
public interface PathPlanner { Optional<List<Vec2>> plan(Vec2 from, Vec2 to, UnitType type, double radius); }
public record Prop(double x, double z, double scale, double rotation, double tint, int kind) {}
public record Road(List<Vec2> points, double width, int kind, boolean[] onBridge) {}
public record Bridge(Vec2 start, Vec2 end, double deckHeight, double width) {}
public interface Terrain extends HeightField {
    WaterField water(); NavGrid nav(); LineOfSight los(); PathPlanner planner();
    List<Road> roads(); List<Bridge> bridges(); List<Prop> trees(); List<Prop> rocks();
    double[] sunDirection(); double shadeAt(double x, double z);
}
public interface TerrainService { Terrain generate(long seed); }   // wires modules 2, 3, 4

// ---- weather
public record WeatherState(double rain, double heavyRain, double snow, double windSpeed,
        double windDirection, double cloud, double wetness, double puddles, double snowCover) {}
public interface WeatherModel {
    void step(int tick); WeatherState state();
    double speedFactor(double dirX, double dirZ); double visibility(); double combatFactor();
}

// ---- rules
public final class UnitData { /* mutable, owned by the engine: id, side, type, x, z, heading, aim,
                                health, state, cooldown, odometer, destroyedTick ... */ }
public record Attack(int attackerId, int targetId, int damage, boolean killed) {}
public interface CombatSystem   { List<Attack> resolve(int tick, List<UnitData> units, Terrain t, WeatherModel w); }
public interface MovementSystem { void step(int tick, List<UnitData> units, Terrain t, WeatherModel w); }

// ---- simulation
public record RoundSetup(int number, long seed, Terrain terrain, List<UnitData> units, WeatherModel weather) {}
public interface ScenarioFactory { RoundSetup create(long masterSeed, int roundNumber); }
public sealed interface SimEvent permits AttackEvent, UnitDestroyedEvent {}
public record UnitView(int id, Side side, UnitType type, double x, double z, double prevX, double prevZ,
        double heading, double prevHeading, double aim, double prevAim,
        int health, UnitState state, double odometer, int destroyedTick) {}
public record RenderSnapshot(int roundNumber, int tick, double timeSeconds, List<UnitView> units, WeatherState weather) {}
public interface Engine {
    void tick(); boolean finished(); Outcome outcome();
    RenderSnapshot snapshot(); List<SimEvent> drainEvents(); long stateHash();
}
public interface EngineFactory { Engine start(RoundSetup setup, SimConfig cfg); }

// ---- session (what the presentation layer sees)
public record SessionView(Phase phase, int roundNumber, Terrain terrain, RenderSnapshot snapshot,
        double alpha, Stats stats, List<SimEvent> events, boolean paused, double speed) {}
public interface Session {
    void advance(double realSeconds); void setPaused(boolean paused); void setSpeed(double speed);
    SessionView view();
}
```

## 5. Cross-cutting rules

- **Determinism:** only DetRandom (bs-math); StrictMath for trigonometry; no wall clock inside the simulation; never let HashMap or HashSet iteration order affect results; process units by ascending id; single-threaded tick; integer tick counter (time = tick x 0.1 s).
- **Threading:** the render thread only reads snapshots; the next round is generated on one worker thread; the session is the only owner of the engine.
- **Units and axes:** metres, seconds, radians; x east, z south; heading is the angle of the vector (cos, sin) in the (x, z) plane.
- **Packages:** ua.battlesim.\<module> is public API, ua.battlesim.\<module>.internal is private.
- **Testing:** JUnit 5; golden-seed tests (seed to state hash); ArchUnit dependency tests; budgets: terrain under 1.5 s, one tick under 1 ms with 40 units.
- **Contract change protocol:** only the architect edits bs-api. After any change, bump its version and re-run every module.

## 6. Build order and milestones

- **Wave 0:** bs-api. Review it by hand before anything else.
- **Wave 1 (parallel):** bs-math, bs-weather, bs-combat, bs-movement, bs-gfx-core.
- **Wave 2 (parallel):** bs-terrain, bs-features, bs-nav, bs-engine.
- **Wave 3 (parallel):** bs-scenario, bs-session, bs-gfx-world, bs-gfx-units, bs-gfx-fx, bs-ui.
- **Wave 4:** bs-tools, then bs-app.

Milestones:

- **M1 headless:** bs-tools plays 20 rounds, results reproducible, no exceptions. This proves the whole core composes.
- **M2 viewer:** window with terrain, water, roads, moving units.
- **M3 complete:** effects, HUD, picking, follow camera, round loop.

Workflow for every part: paste the preamble, the bs-api sources and the module prompt into the AI; run `./gradlew :module:check`; review; commit; move to the next part.

## 7. Prompt pack

### Preamble (paste before every prompt)

```
You are a senior Java engineer. Project: Battle Sim, an autonomous simulator of two sides (BLUE in the north, RED in the south) on procedurally generated terrain. Stack: Java 24, Gradle 8.14.6 multi-module (Kotlin DSL), JUnit 5, package root ua.battlesim.
Rules: (1) implement ONLY the module named below and depend only on the modules listed; (2) code against the bs-api contracts exactly as given; if you believe a contract must change, stop and propose the change separately; (3) be deterministic: no wall clock, no unseeded random, no hash-order dependence, StrictMath for trigonometry; (4) public API in ua.battlesim.<module>, everything else in .internal; (5) immutable records for data; (6) write unit tests that use fakes of neighbouring modules; (7) deliver build.gradle.kts, sources, tests and a short README; ./gradlew :<module>:check must pass; (8) no TODOs, no placeholders.
```

### P0 - bs-api

```
MODULE: bs-api (no dependencies, pure Java 24).
Create every contract from the architecture document (section 4) as Java source: records, enums, sealed interfaces, interfaces, with Javadoc stating units (metres, seconds, radians; x east, z south) and thread-safety.
Add: SimConfig.defaults() (mapSize 2000, step 0.1, attackIntervalTicks 20, maxRoundTicks 18000, readyTicks 20, units 1..20, weatherEnabled false); argument validation in record constructors; UnitData as a mutable class (id, side, type, x, z, heading, aim, health 0..100, state, cooldown, odometer, destroyedTick); UnitInfo with radius, speed, turn rate, eye height, range and the damage table (attacker -> targets tank / infantry / APC infantry: tank 20/50/25 at 500 m; infantry 10/50/20 at 200 m; APC infantry 10/50/20 at 300 m).
No logic beyond validation and lookup. Tests: validation, damage table, defaults.
Acceptance: ./gradlew :bs-api:check passes; no dependency except JUnit.
```

### P1 - bs-math

```
MODULE: bs-math (depends on bs-api only).
Implement: DetRandom (SplitMix64; nextInt(bound), nextLong, nextDouble, nextFloat, nextBoolean, fork(long salt) for independent streams); PerlinNoise(seed) with get(x, y) in about [-1, 1], fbm(x, y, octaves, lacunarity, gain) and ridged(...); Smooth (smoothstep, lerp, clamp); Polylines (chaikin(points, iterations) keeping endpoints, resample(points, step), distanceToSegment); FloatGrid(width, height) with bilinear and nearest sampling.
Rules: no static mutable state; StrictMath for trig and pow.
Tests: same seed gives identical sequences; value ranges; chaikin keeps endpoints; resample spacing within 1 percent; mean of nextDouble near 0.5.
```

### P2 - bs-terrain

```
MODULE: bs-terrain (depends on bs-api, bs-math).
Public API: TerrainBase TerrainGenerator.generate(long seed, SimConfig cfg) returning a HeightField, a WaterField (depth, flow, swamp), a forest-density mask, a baked shade map and a sun direction. No roads, props or navigation here.
Algorithm: height grid (cell about 3.9 m for a 2000 m map) from layered fbm: large relief (wavelength about 1100 m, amplitude 55 m), hills (300 m, 16 m), fine detail, minus narrow ravines from ridged noise (up to 9 m deep). 1-2 rivers: coarse A* across the map preferring downhill, Chaikin smoothing, monotone water level below the bank, carved channel (half width 9-16 m) with sloping banks, flow vectors along the path. 1-2 lakes on the flattest of 40 candidate sites (bowl carving, level = lowest rim). Swamps on low, flat areas: mask plus shallow water. Bake terrain shadows by marching toward a random sun (elevation 26-56 degrees).
Reference behaviour: port the TerrainGenerator of the existing monolith (paste it here).
Tests: determinism per seed, no NaN, river level never rises downstream, depth >= 0, generation under 1.5 s.
```

### P3 - bs-features

```
MODULE: bs-features (depends on bs-api, bs-math).
Public API: TerrainFeatures FeaturePlacer.place(HeightField h, WaterField w, long seed) returning roads, bridges, trees, rocks and a road-corridor mask.
Roads: A* on a 128x128 cost grid (slope, water and swamp penalties; cells already holding a road cost x0.35) for: the main north-south road (always present), a west-east road, 1-2 branches, 3-5 trails; Chaikin smoothing and resampling every 5 m; widths 6.5 / 5 / 2.4 m. Bridges where water depth exceeds 0.25 m, extended by 3 samples on each side, deck 0.8 m above the highest water level.
Trees: dense forest from noise (13 m jittered grid, smoothstep density) plus scattered trees (60 m grid); two kinds (conifer at altitude); skip water, swamp, steep slopes and the road corridor. Rocks: clusters, more on slopes, scale 0.5-2.4.
Tests: the main road spans north to south, no prop inside a road corridor, deterministic output, sane counts.
```

### P4 - bs-nav

```
MODULE: bs-nav (depends on bs-api, bs-math).
Public API: NavService.build(HeightField, WaterField, TerrainFeatures) returning a NavGrid, a LineOfSight and a PathPlanner (interfaces from bs-api).
Grid 128x128 (about 15.6 m cells). A cell is blocked by water deeper than 0.45 m, swamp, slope above 32 degrees, any tree, or a rock of scale >= 1.4; road cells are always passable. Compute a clearance penalty, connected components (8 neighbours, no corner cutting) and the main component; guarantee a north-south route (fallback corridor).
walkable(x, z, r): circle versus blocked squares. LineOfSight: sample every 6 m against terrain and dense forest (10 m occluder), ignore 10 m at both ends.
PathPlanner: weighted A* (slope penalty, road bonus 0.6 for vehicles and 0.8 for infantry, 0.2 per blocked neighbour) followed by string pulling that keeps the unit circle clear; returns empty when the two points are in different components.
Tests: path validity, no path across components, line of sight blocked by a hill, typical plan under 10 ms.
```

### P5 - bs-weather

```
MODULE: bs-weather (depends on bs-api, bs-math).
Public API: SimpleWeatherModel(long seed, boolean enabled) implements WeatherModel.
Precipitation states are exclusive (clear, rain, heavy rain, snow); strong wind (12-22 m/s) is independent. A new random target is chosen every 2-6 minutes; intensities blend with a 40 s time constant. Track wetness, puddles and snow cover (rise while precipitating, decay otherwise, snow melts faster when clear).
speedFactor(dir) = 1 - 0.08 rain - 0.22 heavy - 0.18 snow - 0.10 snowCover - 0.05 puddles + 0.004 x tailwind, clamped to 0.4..1.1. visibility = 25000 / (1 + 5 rain + 24 heavy + 10 snow) metres. combatFactor = clamp(visibility / 5000, 0.3, 1). conditions() lists Sunny, Rain, Heavy rain, Strong wind, Snow.
When enabled is false the weather is permanently clear with 2 m/s wind.
Tests: determinism, exclusivity, factors in range, smooth transitions.
```

### P6 - bs-combat

```
MODULE: bs-combat (depends on bs-api).
Public API: StandardCombatSystem implements CombatSystem.
Per tick: (1) every active unit decrements its cooldown and picks the nearest enemy within range = min(tableRange x weather.combatFactor, weather.visibility) that is visible through terrain.los(); ties go to the lowest id. (2) Units whose cooldown is 0 attack; all damage is collected first and applied simultaneously, then cooldown = attackIntervalTicks. (3) Health clamps at 0 and the unit becomes DESTROYED. Return the Attack records (with killed flag). A unit with a target is ATTACKING and turns its aim toward it (3 rad/s for tanks, 4 rad/s otherwise).
Tests: all nine damage pairs, mutual kill gives two destroyed units, no shot without line of sight, range shrinks in bad visibility, interval is exactly 20 ticks.
```

### P7 - bs-movement

```
MODULE: bs-movement (depends on bs-api).
Public API: StandardMovementSystem implements MovementSystem.
For every active unit that is not attacking: choose the nearest reachable enemy (same nav component) and keep it until another is at least 40 percent closer (hysteresis); replan every 10-14 s or when the target moved more than 150 m; follow waypoints (reached within 6 m). Turn at the type turn rate; speed = baseSpeed x slope factor x road factor (vehicle 1.35, infantry 1.1) x weather.speedFactor x alignment. Never overlap another unit (wrecks included): try deflection angles up to 77 degrees, then a 360 degree sweep. Stuck detection: less than 4 m progress in 80 ticks triggers replanning and a return to the cell centre; after 6 repeats ignore wrecks. No reachable enemy gives WAITING. Update odometer for animation.
Tests with a fake PathPlanner and NavGrid: reaches the target, avoids overlap, recovers from a dead end, deterministic.
```

### P8 - bs-scenario

```
MODULE: bs-scenario (depends on bs-api, bs-math).
Public API: StandardScenarioFactory(TerrainService, SimConfig) implements ScenarioFactory.
create(masterSeed, roundNumber): derive the round seed with SplitMix64(master + number x golden-ratio constant); generate terrain through TerrainService; build each side independently with 1-20 units of random types; BLUE only in the northern 35 percent of the map, RED only in the southern 35 percent, 120 m side margin; positions only inside the main nav component, walkable with radius + 1 m, footprints at least 4 m apart; start state READY, health 100, heading toward the enemy side, unique ids; weather model seeded from the round seed.
Tests: determinism, counts in range, placement rules, unique ids, 200 seeds never fail (use a fake TerrainService).
```

### P9 - bs-engine

```
MODULE: bs-engine (depends on bs-api).
Public API: DefaultEngineFactory(CombatSystem, MovementSystem) implements EngineFactory.
tick(): advance the tick counter (time = tick x 0.1 s); save previous x, z, heading and aim for interpolation; weather.step; keep all units READY during the first readyTicks; then combat.resolve, emit AttackEvent and UnitDestroyedEvent, then movement.step; finally check the end: one side fully destroyed gives a win for the other, both destroyed in the same tick gives DRAW, maxRoundTicks reached gives DRAW_TIME.
snapshot() returns an immutable RenderSnapshot; drainEvents() empties the queue; stateHash() hashes all unit fields (for determinism tests).
Tests with fake systems: phase order, end rules, snapshot immutability, identical hash for identical runs.
```

### P10 - bs-session

```
MODULE: bs-session (depends on bs-api).
Public API: DefaultSession(ScenarioFactory, EngineFactory, SimConfig, long masterSeed) implements Session.
Phases: LOADING, RUNNING, INTERMISSION (80 ticks), GENERATING. advance(realSeconds): accumulate realSeconds x speed, run fixed 0.1 s ticks, at most 300 per call (drop the remainder), do nothing while paused. Speed 0.5-10 with presets 0.5 / 1 / 2 / 3 / 5 / 10. Generate round N+1 on one daemon worker thread while round N runs; swap when the intermission ends, stay in GENERATING if it is not ready yet. Keep statistics: rounds completed, blue wins, red wins, draws, time draws, last result and duration. view() exposes phase, round number, terrain, snapshot, interpolation alpha (accumulator / step), stats, drained events, paused and speed.
Tests: fixed-step independence from frame rate, pause freezes time, prefetch works, statistics, no unbounded loop.
```

### P11 - bs-tools

```
MODULE: bs-tools (depends on all core modules, no graphics).
Provide a headless CLI: --seed N --rounds N --report file. It wires the real modules (terrain, features, nav, weather, combat, movement, scenario, engine, session), plays the rounds at maximum speed and prints a table: round, outcome, simulated time, survivors, wall time. It also runs every round twice and compares stateHash (determinism check) and reports ticks per second.
Include a golden-seed test: for seeds 1..5 outcomes and hashes are stored in a resource file; the test fails when they change.
Acceptance: 20 rounds without exceptions, determinism OK, more than 1000 ticks per second with 40 units.
```

### P12 - bs-gfx-core

```
MODULE: bs-gfx-core (depends on bs-api, LWJGL 3.3.x, JOML).
Provide: Window (GLFW, OpenGL 3.3 core, MSAA, fullscreen by default, F11 toggle, HiDPI-correct sizes, macOS -XstartOnFirstThread documented), ShaderProgram (compile, uniform cache, clear error messages), Mesh and InstancedMesh, Texture, OrbitCamera (pan, rotate, zoom, follow a target position, clamps, view-projection and inverse matrices), InputRouter turning raw events into UiCommand records (TogglePause, SpeedUp, SpeedDown, FocusSelected, ResetCamera, ToggleFullscreen, Click(x, y), DragPan, DragRotate, Zoom).
No game rules. Provide a demo main that opens a window and draws a rotating triangle.
Tests: camera maths without GPU; the rest is verified by the demo.
```

### P13 - bs-gfx-world

```
MODULE: bs-gfx-world (depends on bs-api, bs-gfx-core).
Public API: WorldRenderer(Terrain) with render(OrbitCamera, LightingParams) and dispose().
Build: terrain mesh from the height grid with normals and attributes (swamp, forest, baked shade); water mesh only where depth > 0 with flow and depth attributes and a soft shoreline; road ribbons draped on the terrain (5 vertices across); bridges (deck, rails, pillars); trees and rocks as combined or instanced meshes with distance culling and wind sway.
GLSL 3.30 shaders: procedural grass, dirt and rock; snow cover; wet darkening; puddles; water with animated flowing normals, Fresnel sky reflection, sun glints and foam; fog from visibility.
Tests: mesh builders give the expected vertex counts and no NaN (no GPU needed); visual check through a demo.
```

### P14 - bs-gfx-units

```
MODULE: bs-gfx-units (depends on bs-api, bs-gfx-core).
Public API: UnitRenderer with render(RenderSnapshot, double alpha, OrbitCamera, Optional<Integer> selectedId).
Procedural models: tank (hull, tracks with scrolling pattern, road wheels, turret, gun), APC infantry (8 wheels, turret, three seated soldiers), infantry squad of four soldiers with walking legs. Side colour on paint and vest. Animate from the snapshot: interpolate position and heading with alpha, tilt to the terrain, wheel rotation from odometer, tank gun along the heading while moving and toward the target while attacking, recoil, destroyed units charred or lying down. Soft blob shadows. Use strong ambient fill so models are never dark.
Tests: model builders and animation maths (angle lerp, tilt) without GPU.
```

### P15 - bs-gfx-fx

```
MODULE: bs-gfx-fx (depends on bs-api, bs-gfx-core).
Public API: EffectsRenderer with onEvents(List<SimEvent>, RenderSnapshot), update(simSeconds), render(camera); SkyRenderer.
Effects use simulated time only (pause and speed are respected). Attack event: muzzle flash, a tracer travelling 0.06-0.8 s, then an impact explosion (flash, fire, dust, sparks, smoke). A destroyed unit becomes visibly destroyed only after the last incoming shell lands, then emits realistic rising smoke continuously and fire for the first 90 s. CPU particle pool (about 24000), distance-sorted soft smoke, additive fire. Weather: rain streaks and snow flakes around the camera target, sky gradient, clouds, sun, lightning flash in heavy rain, light colour from WeatherState.
Tests: particle pool and emitter maths without GPU.
```

### P16 - bs-ui

```
MODULE: bs-ui (depends on bs-api, bs-gfx-core).
Public API: HudRenderer with render(SessionView, Optional<UnitView> selected); Picker.pick(x, y, SessionView, OrbitCamera).
HUD (Ukrainian strings from a ResourceBundle): round number, simulated time / 30:00, state (running, paused, round finished, generating), speed buttons and label, weather and visibility, active and destroyed counts per side, totals (rounds, blue wins, red wins, draws), selected unit panel (id, side, type, coordinates, compass heading, health bar, state, target), round-result banner, key hints. NATO markers above every unit at constant pixel size: friendly rectangle, hostile diamond; tank = oval + X (brigade), infantry = cross + I (company), APC infantry = cross + oval + II (battalion); destroyed units dimmed.
Picking: marker hit-test first, then ray versus unit spheres.
Tests: picker and layout maths, symbol rasteriser (no GPU).
```

### P17 - bs-app

```
MODULE: bs-app (depends on every module).
Composition root only; no new logic. Parse --seed, --windowed, --config. Read battle-sim.properties (map size, weather on/off, unit scale). Wire: the terrain pipeline (bs-terrain + bs-features + bs-nav) behind TerrainService; SimpleWeatherModel; StandardCombatSystem; StandardMovementSystem; StandardScenarioFactory; DefaultEngineFactory; DefaultSession; the renderers. Main loop: poll input, translate UiCommand to session calls, session.advance(dt), render world, units, effects, HUD. Gradle application plugin with LWJGL natives per OS and JVM flags (--enable-native-access=ALL-UNNAMED, -XstartOnFirstThread on macOS).
Add ArchUnit tests that enforce the dependency rules of section 2, and a smoke test that runs the headless path for 3 rounds.
```

## 8. Composition checklist

1. `./gradlew check` is green for every module.
2. bs-tools: 20 rounds, determinism OK, throughput target met.
3. bs-app in a window with a fixed seed: terrain visible, units advance toward each other, attacks resolve, smoke appears, rounds repeat, pause, speed, selection and follow camera work.
4. Compare with the monolith: the same seed should give similar outcome statistics (identical results are not required).
5. Performance: 60 FPS with 40 units on the target machine.

If integration fails, send the AI a bug-report prompt: the failing test, the module name, the relevant contract excerpt, and the instruction to fix only that module.
