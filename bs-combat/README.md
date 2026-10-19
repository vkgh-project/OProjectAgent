# bs-combat

Deterministic combat rules for the Battle Sim (layer 3, public API
`ua.battlesim.combat`, private helpers in `ua.battlesim.combat.internal`).

## Public API

- `StandardCombatSystem` — implements the bs-api `CombatSystem` contract.
  - `new StandardCombatSystem()` — default attack interval of 20 ticks
    (`SimConfig.defaults().attackIntervalTicks()`).
  - `new StandardCombatSystem(int attackIntervalTicks)` — custom interval
    (`>= 1`).
  - `resolve(tick, units, terrain, weather)` — resolves all attacks of one
    tick and returns the `Attack` records in ascending attacker-id order.
    Depends only on `bs-api`.

## Behaviour per tick

Units are always processed in ascending id order, independent of the order
they are passed in:

1. Every active unit (not `DESTROYED`, health > 0) decrements its cooldown
   (clamped at 0) and picks a target: the nearest enemy that is active,
   within effective range
   `min(UnitInfo.range(type) x weather.combatFactor(), weather.visibility())`
   metres, and visible through `terrain.los()` (eye heights from
   `UnitInfo`); exact distance ties go to the lowest enemy id.
2. Units whose cooldown is 0 shoot. All damage is collected first and applied
   simultaneously (a unit killed this tick still shoots); health clamps at 0
   and the unit becomes `DESTROYED` with `destroyedTick = tick`. Every
   shooter's cooldown is then set to the attack interval, so consecutive
   shots of one unit are exactly `attackIntervalTicks` ticks apart.
3. Damage per hit comes from the shared `UnitInfo` damage table (attacker
   type x target type); the `killed` flag reports whether the victim ended
   the tick at 0 health.

A unit with a target is set to `ATTACKING` and turns its aim toward the
target at 3 rad/s for tanks and 4 rad/s otherwise (per tick: rate x 0.1 s,
shortest arc, snapping to the exact direction once within one step). Units
without a target are left in the state set earlier in the tick.

## Determinism and threading

Pure function of its arguments; no wall clock, no unseeded randomness;
`StrictMath` for transcendental calls; no `HashMap`/`HashSet` iteration
order; plain lists and arrays only. `resolve` mutates only the
caller-owned `UnitData` list and must be called from the single simulation
worker thread, after the movement step of the same tick.

## Tests

JUnit 5, fakes of the neighbouring modules (terrain, line of sight, weather)
live in `src/test/java/ua/battlesim/combat/`: all nine damage pairs, mutual
kill, no shot without line of sight, range shrinkage in bad visibility and
with low combat factor, the exact 20-tick attack interval, aim turn rates,
target tie-breaking and list-order determinism.

```
./gradlew :bs-combat:check
```