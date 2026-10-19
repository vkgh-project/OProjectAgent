# bs-features

Deterministic placement of the static map features (roads, bridges, trees,
rocks) for the Battle Sim project. Sits at Layer 1 and depends only on
`bs-api` (contracts) and `bs-math` (deterministic RNG / noise / polylines).

## Public API

- **`TerrainFeatures`** - immutable record containing:
  - `List<Road> roads` - road polylines (with per-point `onBridge` flags, width, kind)
  - `List<Bridge> bridges` - the bridges that cross water
  - `List<Prop> trees` - the placed trees
  - `List<Prop> rocks` - the placed rocks
  - `RoadCorridor roadCorridor` - the road-corridor keep-out mask

- **`RoadCorridor`** - immutable `cells x cells` boolean mask of the road
  corridor, with `inCorridor(x, z)` and `corridorCellCount()`.

- **`FeaturePlacer.place(HeightField h, WaterField w, long seed)`** - the
  entry point; returns an immutable, deterministic `TerrainFeatures`.

## Algorithm

1. **Roads** (`RoadPlanner`): A* over a 128x128 cost grid where each cell cost
   is `1 + slope*4 + depth*12 + swamp*6`. Cells already holding a road cost
   `x0.35` so later roads reuse the network. Planned in a fixed order — main
   north-south road (always present, north edge to south edge), a west-east
   road, 1-2 branches off the main road and a few short trails. Each raw cell
   path is smoothed with 2 Chaikin iterations and resampled every 5 m.

2. **Bridges**: along each resampled road, a bridge spans a maximal run of
   samples where water depth exceeds `0.25 m`, extended by 3 samples on each
   side, with a deck `0.8 m` above the water surface. The same samples are
   flagged in `Road.onBridge`.

3. **Road corridor** (`RoadCorridor`): a 256x256 mask painted with a 10 m
   keep-out radius around every road centre point; props are rejected inside
   it.

4. **Trees** (`Props`): a jittered ~13 m grid where a smoothstep of a
   noise-driven density is high produces a tree, plus a coarser ~60 m
   scatter. A tree is rejected if it falls inside the corridor, in water
   (`depth > 0.1`), in deep swamp (`swamp > 0.5`), on steep ground
   (`slope > 0.5`), or off-map. Kind is `0` (broadleaf) below the mean height
   and `1` (conifer) above it.

5. **Rocks** (`Props`): 6-11 slope-biased clusters (more rocks on steeper
   ground, scale `0.5`-`2.4`), each rock kept off water and outside the
   corridor.

## Determinism

The same `(heightField, waterField, seed)` always produces an equal
`TerrainFeatures`. No wall clock, no unseeded randomness, `StrictMath` for
trigonometry, and candidates are visited in a fixed grid order so that
hash/iteration order never influences the result. The `A*` priority queue is
keyed by `(f-cost, insertion order)` — a total order — so the expanded cells,
and therefore the path, are identical for identical inputs.

## Tests

JUnit 5 tests use deterministic fakes of the bs-api `HeightField` / `WaterField`
contracts and assert the main-road span, the corridor keep-out, determinism,
sane counts, and in-bounds endpoints.

## Build

```bash
./gradlew :bs-features:check
```