# bs-movement

Deterministic movement rules for Battle Sim. Implements the `MovementSystem`
contract from **bs-api** (public API: `ua.battlesim.movement.StandardMovementSystem`);
this module depends only on bs-api.

## Behaviour

Per tick, units are processed in ascending id order. Every active unit that
is not `ATTACKING`:

1. **Targeting** — picks the nearest enemy on the same nav component for
   which the terrain `PathPlanner` has a path. The target is kept until
   another enemy is at least 40 % closer (hysteresis), so units do not
   flip-flop. No reachable enemy → `WAITING`.
2. **Pathing** — replans when the target is first acquired or switched,
   when the target moved more than 150 m since the last plan, or on the
   unit's personal 100–140 tick interval (10–14 s, chosen deterministically
   as `100 + id % 5 * 10`). Waypoints count as reached within 6 m.
3. **Range** — a unit within its effective weapon range
   (`min(type range * weather.combatFactor(), weather.visibility())`, the
   same range the combat system uses) holds position → `READY`.
4. **Speed** — `baseSpeed * slopeFactor * roadFactor * weather.speedFactor
   * alignment`; road factor is 1.35 for vehicles, 1.1 for infantry, 1.0
   off-road; slope factor is `max(0.5, 1 - 2*grade)` for ascents (1.0 for
   flat/descending); alignment is `max(0, cos(heading - move direction))`.
   Heading turns at the type turn rate (`UnitInfo`); the odometer
   accumulates the distance actually travelled (for animation).
5. **No overlap** — a step that would touch another unit (wrecks
   included) is rejected and replaced by deflection angles up to 77°
   (7.7° steps, + then −), then a full 360° sweep (36 × 10°); the first
   collision-free, walkable candidate is used; otherwise the unit holds.
6. **Stuck detection** — less than 4 m of progress within 80 ticks triggers
   a replan starting from the navigation cell centre; after 6 consecutive
   stuck windows the unit ignores wrecks.

## Determinism

Pure function of its arguments; ascending id order; `StrictMath` only; no
wall clock, no unseeded randomness, no hash-order dependence (per-unit
motion state is accessed by unit id only).

## Tests

JUnit 5 tests use fakes of the neighbouring modules (`FakeTerrain`,
`FakeNavGrid`, `FakePlanner`, `FakeWeather`): reaches the target and stops
in weapon range, avoids overlap with a blocking unit, recovers from a dead
end via the stuck/replan path, target hysteresis and waiting states, road /
slope / weather speed factors, and bit-identical determinism (including
input-order independence).

```
./gradlew :bs-movement:check
```