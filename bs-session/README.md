# bs-session

Deterministic session orchestration for Battle Sim. Implements the `Session`
contract from **bs-api** (public API: `ua.battlesim.session.DefaultSession`);
this module depends only on bs-api.

## Public API

- `DefaultSession(ScenarioFactory, EngineFactory, SimConfig, long masterSeed)`
  — a `Session`. Round 1 is built synchronously in the constructor so
  `view()` is valid immediately; round 2 is handed to a single daemon worker
  thread.
- `DefaultSession.stats()` — an immutable `SessionStats` (rounds completed,
  blue/red/draw/time-draw counts, last outcome and its simulated duration).

## Behaviour

- **Fixed timestep.** `advance(realSeconds)` accumulates `realSeconds x speed`
  and runs whole `stepSeconds` ticks, at most `300` per call — the excess is
  dropped so the call stays bounded and `alpha = accumulator / step` always
  stays in `[0, 1)`. Ticks run even across chunked calls: `advance(1.0)` and
  `advance(0.1) x 10` produce the same number of ticks.
- **Pause.** While paused, `advance` does nothing (no ticks, no accumulation).
- **Prefetch.** The next round is generated on one daemon worker thread while
  the current round runs. When a round finishes the session spends 80 ticks in
  `INTERMISSION` and then swaps in the prefetched round; if the worker is not
  ready yet it stays in the intermission and retries. The worker only ever calls
  `ScenarioFactory`/`EngineFactory` and never touches a live engine.
- **Statistics.** The session tracks rounds completed, blue/red wins, draws,
  time draws, the last outcome and its duration, and exposes the per-round
  surviving-unit counts (`Stats`) through `view()`.
- **Events.** Engine events are drained each tick and delivered (exactly once)
  through `view().events()`.

## Threading

`advance`/`setPaused`/`setSpeed`/`view`/`stats` are guarded by a single lock, so
the UI thread and the render thread never observe a torn state. The engine is
created and advanced only on the thread calling `advance` (one simulation
worker); the daemon worker thread only generates future rounds.

## Determinism

The simulation is a pure function of its inputs; the only wall-clock influence
is how fast ticks are produced. No unseeded randomness, no hash-order dependence
(rounds are always taken in ascending order), `StrictMath` for the fixed-step
math.

## Tests

JUnit 5 tests use fakes of the neighbouring modules (`FakeScenarioFactory`,
`FakeEngineFactory`, `FakeEngine`, `FakeTerrain`, `FakeWeather`):
fixed-step independence from frame rate, speed scaling, pause freezing time,
prefetch-and-swap, aggregate statistics, and the bounded per-call cap.

```
./gradlew :bs-session:check
```