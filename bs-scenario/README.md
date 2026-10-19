# bs-scenario

Deterministic round (scenario) construction for the Battle Sim project.

## Public API (`ua.battlesim.scenario`)

- **`StandardScenarioFactory(TerrainService, SimConfig)`** — implements the
  bs-api `ScenarioFactory` contract:
  `RoundSetup create(long masterSeed, int roundNumber)`.

Internal (`ua.battlesim.scenario.internal`):

- **`RoundWeather(long seed, boolean enabled)`** — a `WeatherModel`
  implementation; the state of a tick is a pure function of `(seed, tick)`.

## Behaviour

- **Round seed**: the first SplitMix64 value of
  `masterSeed + roundNumber x 0x9E3779B97F4A7C15` (the golden-ratio step
  constant of `DetRandom`). The same `(masterSeed, roundNumber)` always gives
  the same `RoundSetup`.
- **Terrain**: generated from the round seed through the injected
  `TerrainService` (bs-terrain wires it; bs-scenario only sees the bs-api
  contract).
- **Units**: each side is built independently (BLUE before RED, so ids are
  ascending `0..n-1`), with `minUnits..maxUnits` units per side (default
  1..20) of uniformly random type (`TANK`, `INFANTRY`, `APC_INFANTRY`).
  BLUE spawns only in the northern 35% of the map, RED only in the southern
  35%, both with a 120 m east/west margin (clamped to `size / 2` on maps
  under 240 m so the band is never empty).
- **Placement**: a candidate position is accepted only when its nav cell is
  passable and belongs to the main connected component, the unit's circle
  plus a 1 m clearance fits on passable cells
  (`NavGrid.walkable(x, z, radius + 1)`), and every placed footprint is at
  least 4 m away (centre distance at least `4 + radius + otherRadius`).
  64 random candidates per unit; on exhaustion a deterministic 2 m grid scan
  of the spawn band finds a spot. If the band has no valid cell at all the
  factory throws `IllegalStateException`.
- **Initial state**: `READY`, health 100, cooldown 0, odometer 0,
  `destroyedTick = -1`, heading and aim toward the enemy side: `+pi/2`
  (south) for BLUE, `-pi/2` (north) for RED.
- **Weather**: `RoundWeather` seeded from the round seed (bs-scenario
  implements the bs-api `WeatherModel` contract itself because same-layer
  modules never depend on each other). With
  `SimConfig.weatherEnabled == false` the model is permanently clear
  (all channels 0, all factors 1).

## Determinism

Only `DetRandom` (bs-math) is consulted and `StrictMath` is used for
trigonometry; no wall clock, no unseeded randomness, no HashMap/HashSet
order dependence, BLUE built before RED, units processed in ascending id
order. The weather state of a tick is a pure function of
`(seed, tick)`.

## Tests (`src/test/java/ua/battlesim/scenario`)

Fakes of neighbouring modules (`FakeTerrainService`, `FakeTerrain`,
`FakeNavGrid`); no real terrain/nav/weather code is exercised.

- determinism: same seed/round identical, different rounds differ;
- unit counts within the configured `[minUnits, maxUnits]` per side;
- placement: spawn bands (35%), 120 m side margin, main nav component,
  `walkable(radius + 1)`, footprint separation;
- initial state: `READY`, health 100, headings toward the enemy;
- unique ascending ids;
- 200 seeds never fail;
- weather seeded from the round seed, disabled mode permanently clear,
  channel and factor bounds, re-step no-op.

## Build

```bash
./gradlew :bs-scenario:check
```