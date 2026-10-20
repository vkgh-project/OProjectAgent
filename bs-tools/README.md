# bs-tools — headless Battle Sim runner

`bs-tools` is the **headless CLI** for the Battle Sim: it wires the real core
modules (terrain, features, nav, weather, combat, movement, scenario, engine and
session) and plays rounds at maximum speed with **no graphics**.

It sits at layer 4, above every core module, and is therefore the only place
that may import all of them at once and glue them into a single pipeline. It
depends on `bs-api`, `bs-math`, `bs-terrain`, `bs-features`, `bs-nav`,
`bs-weather`, `bs-combat`, `bs-movement`, `bs-scenario`, `bs-engine` and
`bs-session`; it has no graphics dependencies.

## What it does

For a master seed and a round number the runner deterministically:

1. generates the terrain (bs-terrain) and places roads/bridges/trees/rocks
   (bs-features),
2. builds the navigation grid, line-of-sight and path planner (bs-nav),
3. places the units and the per-round weather (bs-scenario),
4. starts the engine (bs-engine) with the standard combat and movement systems
   (bs-combat, bs-movement) and advances it tick by tick until it finishes.

The same `(seed, round)` always produces the same result and the same
`stateHash`, so a round can be played twice and the two hashes compared as a
determinism check. The session (bs-session) is also wired and available through
`BattleSimRunner#newSession()`.

## Public API (`ua.battlesim.tools`)

| Type | Purpose |
| --- | --- |
| `BattleSimRunner` | the runner: wires the modules, `play(round)`, `run()`, `measureThroughput(...)`, `probe(units)`, `goldenLine()`/`goldenBlock()`, `newSession()` and the CLI `main` |
| `RoundResult` | one round: `round`, `outcome`, `simulatedSeconds`, `survivorsBlue/Red`, `stateHash`, `wallSeconds` |
| `RunReport` | a run: per-round results, `determinismOk`, `totalTicks`, wall time, `ticksPerSecond` |
| `ToolOptions` | the parsed CLI flags |

Everything private lives in `ua.battlesim.tools.internal` (`WiredTerrain`,
`WiredTerrainService`).

## Running the CLI

```
./gradlew :bs-tools:runTools --args="--seed 1 --rounds 5 --report out.txt"
```

Flags (all optional, order-independent):

- `--seed N` master seed (default `1`)
- `--rounds N` number of rounds to play (default `5`)
- `--units N` force exactly `N` units per side (min = max = N)
- `--max-ticks N` bound each round's length (default `18000`)
- `--report FILE` write the run report (a table) to `FILE`
- `--golden` print the golden-seed lines (seeds 1..5, round 1) — a regeneration tool
- `--perf` run a performance probe (40 units) and print terrain/setup/per-tick costs

Example output:

```
Battle Sim headless run  (seed=1)
config: SimConfig[...]
round  outcome    simulated(s)   survivors  wall(s)    stateHash
1      DRAW_TIME  200.0          22         1.402      -4746963212507906246
...
------------------------------------------------------------
determinism: OK
ticks: 10000
wall: 12.304 s
throughput: 1742.5 ticks/s
```

`runCli` returns `0` on success, `2` when a determinism check fails, `64` on a
bad command line and `70` on an I/O error.

## Tests

Run with:

```
./gradlew :bs-tools:check
```

- `GoldenSeedTest` — for master seeds 1..5 the round-1 outcome and state hash
  are stored in `src/main/resources/ua/battlesim/tools/golden-seeds.txt`; the
  test **fails when any of them changes** (a non-deterministic or behavioural
  change in the pipeline). Regenerate with `--golden`.
- `DeterminismTest` — each round is played twice and the two `stateHash` values
  (plus outcome and survivors) must be identical.
- `ThroughputTest` — the simulation loop sustains **> 1000 ticks/s with 40
  units** (20 per side) in the steady state (initial path-planning warm-up
  excluded).
- `RoundCompletionTest` — 20 rounds complete without exceptions.
- `SessionSmokeTest` — the wired session's initial view is valid and it advances.
- `CliTest` — option parsing, the golden and report modes and error handling.

## Determinism & budgets

- Determinism: only `DetRandom` (bs-math) and `StrictMath` are used inside the
  simulation; units are processed in ascending id order; no wall clock and no
  hash-order dependence. The runner's only wall-clock use is `System.nanoTime()`
  to *measure* wall time, which never feeds back into the simulation state.
- Budgets: a 2000 m map generates well under 1.5 s; one tick with 40 units is
  well under 1 ms in the steady state (> 1000 ticks/s).
