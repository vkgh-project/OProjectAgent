# bs-math

Deterministic math and numeric utilities of Battle Sim, public API in
`ua.battlesim.math`.

**Layer 0 — depends on `bs-api` only** (for the shared `Vec2` point type).
Every other module may depend on it; it never depends on them.

## API

| Type | Purpose |
| --- | --- |
| `DetRandom` | SplitMix64 PRNG: `nextLong`, `nextDouble`, `nextFloat`, `nextInt(bound)` (unbiased, rejection-based), `nextBoolean`, and `fork(long salt)` which derives an independent child stream from the parent state without consuming parent values |
| `PerlinNoise` | Seeded 2-D Perlin gradient noise: `get(x, y)` in roughly `[-1, 1]`, plus normalised `fbm(x, y, octaves, lacunarity, gain)` in `[-1, 1]` and `ridged(x, y, octaves, lacunarity, gain)` in `[0, 1]` |
| `Smooth` | Static `clamp(v, min, max)`, `lerp(a, b, t)`, `smoothstep(edge0, edge1, x)` and `smoothstep(t)` |
| `Polylines` | Static `chaikin(points, iterations)` (endpoints kept), `resample(points, step)` (equal spacing along the path, endpoint preserved), `distanceToSegment(p, a, b)` in metres |
| `FloatGrid` | `width x height` row-major float grid with `nearest(x, z)` and `bilinear(x, z)` sampling; out-of-range samples clamp to the nearest edge |

All constructors and methods validate their arguments and throw
`IllegalArgumentException` (or `NullPointerException` for null collections)
instead of silently degrading.

## Determinism and thread-safety

- The only randomness source in the simulation: seeded SplitMix64, no wall
  clock, no `ThreadLocalRandom`, no hash-order dependence.
- `StrictMath` is used for transcendental calls (`floor`, `abs`, `sqrt`);
  everything else is plain IEEE-754 arithmetic, so the same inputs always
  produce the same outputs.
- No static mutable state: `Smooth` and `Polylines` are stateless static
  utilities; `DetRandom`, `PerlinNoise` and `FloatGrid` keep all state in
  instance fields.
- `DetRandom` and `FloatGrid` are mutable and intended for the single
  simulation/generation worker thread; `PerlinNoise` and the static
  utilities are immutable and thread-safe.
- `Polylines` outputs are new immutable lists; `FloatGrid.values()` returns
  a defensive copy.

## Build

```
./gradlew :bs-math:check
```