# bs-gfx-fx

Battle effects and sky rendering for the Battle Sim project (Layer 3).
Depends only on `bs-api` and `bs-gfx-core`.

## Public API

| Class | Description |
|-------|-------------|
| `ua.battlesim.gfx.fx.EffectsRenderer` | `onEvents(List<SimEvent>, RenderSnapshot)`, `update(simSeconds)`, `render(camera)` — muzzle flashes, tracers (0.06–0.8 s travel), impact explosions (flash, fire, dust, sparks, smoke), wrecks that become visible after the last killing shell lands and emit rising smoke + fire for the first 90 s, rain streaks and snow flakes around the camera target |
| `ua.battlesim.gfx.fx.SkyRenderer` | `render(WeatherState, simSeconds, sunDirection, camera)` — sky gradient, sun, procedural clouds and lightning flash in heavy rain; `colors(WeatherState)` and `lightningFlash(WeatherState, simSeconds)` as pure static helpers |
| `ua.battlesim.gfx.fx.SkyColors` | Immutable palette record derived from a `WeatherState` (sky, sun, light and cloud colours) |

All three implement `AutoCloseable`; call `dispose()` before releasing the
OpenGL context (safe to call multiple times, safe before any `render`).

## Behaviour rules

- **Simulated time only** — effects advance via `update(simSeconds)`; pause
  and playback speed are respected automatically. No wall clock.
- **Deterministic** — a fixed seed drives all random choices (deterministic
  splitmix64), `StrictMath` for trigonometry, and no hash-order dependence
  (events in list order, wrecks in ascending unit id, pools in slot order).
- **CPU particle pool** — 24000 slots by default, partitioned per effect
  type (smoke 8192, fire 4096, sparks 4096, dust 2048, rain 2560, snow
  2240, flashes 768) so precipitation can never evict combat effects.
- **Draw order** — soft smoke, dust and precipitation alpha-blended and
  sorted far-to-near by distance to the eye; fire, sparks and flashes
  additive. Tracers are camera-facing quads with a bright shell tip.
- **Weather** — rain streaks and snow flakes spawn in a box around the
  camera target (intensity from `WeatherState`), the sky palette and light
  colour come from `SkyColors`, lightning is a deterministic function of
  simulated time and heavy-rain intensity.

## Internal (`ua.battlesim.gfx.fx.internal`)

- `EffectsModel` — the deterministic CPU effect simulation (testable)
- `ParticlePool`, `ParticleType`, `ParticleState` — fixed pool, ring
  overwrite per type, per-type physical model
- `TracerPool` — in-flight shells, travel time `distance / 600 m/s` clamped
  to `[0.06 s, 0.8 s]`
- `WreckTracker` — wreck visibility (after last killing shell) and the 90 s
  smoke/fire window
- `WeatherSpawner`, `Lightning` — precipitation spawning and the lightning
  schedule (pure functions of simulated time)
- `FxRandom` — deterministic splitmix64 source
- `FxShaders`, `ParticleVao`, `SolidVao`, `SkyVao` — GLSL 3.30 sources and
  GPU vertex arrays (created lazily on the first `render`)

## Build

```
./gradlew :bs-gfx-fx:check
```

## Tests (no GPU required)

- `FxRandomTest` — determinism and ranges of the random source
- `ParticlePoolTest` — blocks, ring overwrite, per-type physics, wind, kill
  plane, determinism, validation
- `TracerPoolTest` — travel-time window, tip progress, expiry order
- `WreckTrackerTest` — visibility after the last shell, 90 s window, id
  order, pruning
- `LightningTest` — zero without heavy rain, range, determinism, decay
- `SkyColorsTest` — palette mapping, bounds, determinism
- `EffectsModelTest` — attack → tracer → impact chain, wreck rules, rain
  around the centre, end-to-end determinism (seed → state fingerprint)
- `EffectsRendererTest`, `SkyRendererTest` — contract validation and safe
  idempotent disposal