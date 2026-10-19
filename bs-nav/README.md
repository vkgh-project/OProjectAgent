# bs-nav

`bs-nav` is the **Layer 1** navigation module of the Battle Sim. Given a round's
terrain it builds the three navigation services defined by the `bs-api` contracts:

* `ua.battlesim.api.NavGrid` – the discrete passability grid, connected
  components and `walkable`/`isRoad` queries;
* `ua.battlesim.api.LineOfSight` – terrain- and forest-aware visibility;
* `ua.battlesim.api.PathPlanner` – terrain-aware A* path planning with string
  pulling.

It depends only on `bs-api` (contracts) and `bs-math` (deterministic
utilities), exactly as the layering rules require. It has **no** dependency on
`bs-terrain` or `bs-features` (same layer): those modules' data arrives through
the public API as the `bs-api` value types (`HeightField`, `WaterField`, `Road`,
`Prop`).

## Public API

```java
NavService.build(HeightField height, WaterField water)
NavService.build(HeightField height, WaterField water,
                 List<Road> roads, List<Prop> trees, List<Prop> rocks)
NavService.build(HeightField height, WaterField water, FeatureSet features)
```

Each call returns an immutable, thread-safe `NavBundle` record:

```java
record NavBundle(NavGrid grid, LineOfSight lineOfSight, PathPlanner planner)
```

`FeatureSet` is a value type that groups the navigation-relevant placed
features (roads, trees, rocks); the composition root extracts it from the
round's terrain features and passes it in. All three services share a single
underlying grid model, so their answers are mutually consistent.

## Behaviour

**Grid.** A `128 x 128` grid (cell size = `mapSize / 128`). A cell is *blocked*
when any of the following hold at the cell centre:

* water depth above `0.45 m`;
* any swamp (swamp factor above zero);
* average cell slope above `32` degrees;
* any tree stands inside the cell;
* a rock with `scale >= 1.4` stands inside the cell.

A cell whose centre lies within half the road width of a road centre line is a
*road cell* and is **always passable**, whatever the other conditions.

Passable cells are grouped into 8-neighbour connected components without corner
cutting; the largest one is the *main component*. If no component connects the
north quarter to the south quarter, a three-cell-wide vertical fallback corridor
is carved down the middle of the map and the components are recomputed, so a
north–south route is guaranteed.

A *clearance penalty* in `[0, 1]` is computed per passable non-road cell from
its slope and shallow-water depth; road and corridor cells carry penalty zero.

**`walkable(x, z, r)`** reports whether a circle of radius `r` metres around
`(x, z)` fits entirely on passable cells inside the map.

**Line of sight.** The sight line is sampled every `6 m`, ignoring the first and
last `10 m` of the segment (props and terrain right at the observer/target are
not counted as blockers). A sample blocks the line when the ground there is at
or above the interpolated sight-line height, or when a tree (a `10 m` occluder)
lies closer than `10 m` to the sample.

**Path planning.** Endpoints outside the map, on a blocked cell, or in different
connected components yield an empty result. Otherwise a weighted A* runs over
the 8-neighbour passable cells (no corner cutting): the edge cost is the
distance times `(1 + clearance penalty of the entered cell)`, multiplied by the
road factor of an entered road cell (`0.6` for vehicles, `0.8` for infantry),
plus `0.2` per blocked neighbour of the entered cell; the heuristic is the
straight-line distance and the open list is a deterministic total-ordered heap.
The resulting cell-centre polyline is shortened by string pulling that keeps the
unit's circle (its radius) clear, from `from` to `to`.

## Determinism

The module is a pure function of its inputs: no wall clock, no unseeded
randomness, `StrictMath` for all trigonometry, plain arrays (no hash-order
dependence) and fixed iteration order throughout. The open-list heap uses a
total order (f, cell, insertion sequence) so the pop order – and therefore the
chosen route – is fully deterministic.

## Build & test

```sh
./gradlew :bs-nav:check
```

Unit tests live in `src/test/java/ua/battlesim/nav` and use a `FakeTerrain`
fake (flat/slope/hill/water/swamp/island terrain) in place of the neighbouring
terrain module. They cover:

* `NavGridTest` – grid passability rules (water, swamp, slope, tree, rock, road
  override), components and the fallback corridor, `walkable`/`isRoad` and
  index validation;
* `LineOfSightTest` – open lines, a hill blocking a line, a tree blocking a
  mid-line sight, endpoint trees being ignored, and argument validation;
* `PathPlannerTest` – a valid route on an open map (endpoints, bounds,
  circle-clearance of every leg), no route across disconnected components, and
  a representative plan finishing inside the 10 ms budget.
