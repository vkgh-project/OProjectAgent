# bs-engine

Deterministic simulation core for Battle Sim. Implements the `Engine` and `EngineFactory` contracts from bs-api.

## Public API

- `ua.battlesim.engine.DefaultEngineFactory` — creates `Engine` instances from a `RoundSetup` and `SimConfig`.

## Tick Sequence

Each `tick()` call performs:
1. Advance the tick counter (time = tick × stepSeconds)
2. Save previous x, z, heading, aim for interpolation
3. Call `weather.step(tick)`
4. If `tick > readyTicks`: call `combatSystem.resolve(...)`, emit `AttackEvent` / `UnitDestroyedEvent`, then call `movementSystem.step(...)`
5. Check end conditions (BLUE/RED fully destroyed, DRAW, DRAW_TIME)

## End Conditions

| Condition | Outcome |
|-----------|---------|
| Both sides fully destroyed same tick | `DRAW` |
| All BLUE units destroyed | `RED_WIN` |
| All RED units destroyed | `BLUE_WIN` |
| `tick >= maxRoundTicks` | `DRAW_TIME` |

## Build

```
./gradlew :bs-engine:check
```

## Dependencies

- `bs-api` (contracts only)