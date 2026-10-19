# bs-weather

Deterministic per-round weather model for Battle Sim.

**Public API** (`ua.battlesim.weather`):

- `SimpleWeatherModel(long seed, boolean enabled)` implements the bs-api
  `WeatherModel` contract (`step(int tick)`, `state()`, `speedFactor(dx, dz)`,
  `visibility()`, `combatFactor()`) and adds `conditions()`.

**Behaviour**

- Precipitation regimes are exclusive: `CLEAR`, `RAIN`, `HEAVY_RAIN`, `SNOW`
  (enum `ua.battlesim.weather.internal.Precip`). A new regime (plus new wind
  targets) is drawn every 2–6 minutes from a seeded `DetRandom` (bs-math).
- All channels approach their targets with a 40 s exponential time constant,
  so transitions are smooth; channels within 1% of their target snap to it
  (convergence cutoff), so a regime that loses the target reaches exactly
  zero within about 2 minutes. At most two channels are ever above 0.05, at
  most one is ever dominant (> 0.5), and a regime three changes old (≥ 240 s)
  is exactly zero.
- Wetness and puddles rise while any precipitation is active and decay
  otherwise; snow cover rises while it snows (about 200 s to full), decays
  otherwise, and melts faster when the regime is clear.
- Strong wind (12–22 m/s) is drawn independently of the precipitation regime
  (50% of regime changes); normal wind is 1–11 m/s. Wind direction is kept
  in `(-pi, pi]`.
- `speedFactor(dir) = 1 - 0.08 rain - 0.22 heavy - 0.18 snow - 0.10 snowCover
  - 0.05 puddles + 0.004 x tailwind`, clamped to `[0.4, 1.1]`.
- `visibility = 25000 / (1 + 5 rain + 24 heavy + 10 snow)` metres.
- `combatFactor = clamp(visibility / 5000, 0.3, 1)`.
- `conditions()` lists `Sunny` (no precipitation above 0.5), `Rain`
  (rain > 0.5), `Heavy rain` (heavy rain > 0.5), `Strong wind`
  (wind >= 12 m/s) and `Snow` (snow > 0.5).
- With `enabled = false` the weather is permanently clear with a 2 m/s wind
  toward +x (east): `speedFactor` is `1 + 0.004 x 2 x dot(dir, +x)`,
  `visibility = 25000`, `combatFactor = 1.0`, `conditions() = [Sunny]`.

**Determinism**

- Only `DetRandom` (bs-math) is consulted; no wall clock, no unseeded random,
  no static mutable state, no hash-order dependence.
- `StrictMath` is used for all transcendental calls (`exp`, `cos`, `sin`).
- Same seed + same tick sequence = byte-identical state sequence (covered by
  the golden-seed test `DeterminismTest.goldenSeedStateHash`).
- Stepping a tick that was already stepped is a no-op; only strictly forward
  ticks advance the model.

**Tests** (`src/test/java/ua/battlesim/weather`)

- `DeterminismTest` — same seed gives identical states, different seeds
  diverge, re-stepping is a no-op, golden-seed state hash.
- `ExclusivityTest` — at most three channels non-zero at any tick, at most
  two above 0.05, at most one dominant (> 0.5), bounded cross-fade total.
- `FactorsTest` — spec formulas recomputed from the published
  `WeatherState`, clamp bounds, disabled mode, `conditions()` consistency.
- `SmoothnessTest` — per-tick change bounds for every channel, valid ranges.

**Build**: `./gradlew :bs-weather:check`
