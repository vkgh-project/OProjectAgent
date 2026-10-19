# bs-api

Shared contracts of Battle Sim: records, enums, sealed interfaces and
interfaces in the public package `ua.battlesim.api`.

**Layer 0 — no dependencies.** This module depends on nothing (tests use
JUnit only, configured at the repository root). Every other module may depend
on it; it never depends on them.

## Units and axes

- metres, seconds, radians throughout;
- x = east, z = south; heading is the angle of the (cos, sin) vector in the
  (x, z) plane;
- time = tick x `stepSeconds` (default 0.1 s per tick).

## Contract inventory

| Group | Types |
| --- | --- |
| Primitives / enums | `Vec2`, `Side`, `UnitType`, `UnitState`, `Outcome`, `Phase` |
| Configuration | `SimConfig` (validated, `SimConfig.defaults()` = 2000 m, 0.1 s, 20, 18000, 20, 1..20, weather off) |
| World | `HeightField`, `WaterField`, `NavGrid`, `LineOfSight`, `PathPlanner`, `Prop`, `Road`, `Bridge`, `Terrain`, `TerrainService` |
| Weather | `WeatherState` (validated), `WeatherModel` |
| Rules | `UnitData` (mutable, owned by the engine; immutable id), `UnitInfo` (per-type parameters + damage table), `Attack`, `CombatSystem`, `MovementSystem` |
| Simulation | `RoundSetup`, `ScenarioFactory`, `SimEvent` (sealed: `AttackEvent`, `UnitDestroyedEvent`), `UnitView`, `RenderSnapshot`, `Engine`, `EngineFactory` |
| Session | `Session`, `SessionView`, `Stats` |

## UnitInfo and the damage table

`UnitInfo` is the single source of truth for per-unit-type parameters
(radius, speed, turn rate, eye height, range) and the damage table. The
range and damage values come from the architecture document:

| Attacker \ Target | TANK | INFANTRY | APC_INFANTRY |
| --- | --- | --- | --- |
| **TANK** (range 500 m) | 20 | 50 | 25 |
| **INFANTRY** (range 200 m) | 10 | 50 | 20 |
| **APC_INFANTRY** (range 300 m) | 10 | 50 | 20 |

Lookups: `UnitInfo.of(type)`, `UnitInfo.range(type)`,
`UnitInfo.damage(attacker, target)` — implemented with enums/switches, no
hash-order dependence.

Values not fixed by the architecture document (radius, speed, turn rate, eye
height) are standardised here so no module redefines them:

| Type | radius (m) | speed (m/s) | turnRate (rad/s) | eyeHeight (m) | range (m) |
| --- | --- | --- | --- | --- | --- |
| TANK | 2.0 | 14.0 | 0.5 | 2.5 | 500 |
| INFANTRY | 0.5 | 2.0 | 2.0 | 1.7 | 200 |
| APC_INFANTRY | 2.0 | 10.0 | 0.4 | 2.2 | 300 |

## Contract decisions (architecture review input)

- **Shared in bs-api:** `Phase` and `Stats` are part of `SessionView`, the
  type the presentation layer consumes, so they are shared contracts.
  `AttackEvent`/`UnitDestroyedEvent` are shared because `SimEvent` is sealed
  and `drainEvents()` returns them to the UI.
- **NOT shared (presentation/feature modules own them):** `TerrainBase` and
  `TerrainFeatures` belong to bs-features/bs-terrain internals, `UiCommand`
  and `LightingParams` belong to bs-ui/bs-gfx-*. Adding them to bs-api would
  force P4 to import bs-features-style types and P8 to import bs-weather-style
  types, which the dependency graph forbids (same-layer modules never depend
  on each other; they talk through bs-api contracts). Rendering-specific
  state must therefore be produced by those modules from `Terrain` /
  `WeatherState` / `RenderSnapshot` — no new shared DTO was added.
- No incompatible requirements were found in the shared-contract section; no
  `CONTRACT_CHANGE_REQUIRED` is reported.

## Validation

Record constructors and `UnitData` setters validate their arguments
(finite doubles, non-negative ticks/ids, health in [0, 100], intensities in
[0, 1], non-empty lists, matching `onBridge` length) and throw
`IllegalArgumentException`. No logic beyond validation and lookup exists in
this module.

## Determinism and thread-safety

All types are immutable except `UnitData` (owned by the engine on the single
simulation worker thread). Values published to the render thread
(`UnitView`, `RenderSnapshot`, `SessionView`, events, `WeatherState`) are
immutable and defensively copied. Javadoc on every type states units and
thread-safety obligations.

## Build

```
./gradlew :bs-api:check
```
