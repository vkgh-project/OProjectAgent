# bs-terrain

Deterministic terrain generation for the Battle Sim project.

## Public API

- **`TerrainBase`** - immutable record containing:
  - `HeightField` - elevation data (bilinear sampling)
  - `WaterField` - depth, flow velocity, and swamp factor
  - `FloatGrid` - forest density mask (values in [0, 1])
  - `FloatGrid` - baked shade map (values in [0, 1])
  - `double[3]` - sun direction (unit vector, x east, y up, z south)

- **`TerrainGenerator.generate(long seed, SimConfig cfg)`** - generates a `TerrainBase`

## Algorithm

1. **Height grid**: 512x512 cells (~3.9 m for a 2000 m map) from layered fBM:
   - Large relief: wavelength ~1100 m, amplitude 55 m
   - Hills: wavelength ~300 m, amplitude 16 m
   - Fine detail: wavelength ~80 m, amplitude 4 m
   - Narrow ravines: ridged noise, up to 9 m deep

2. **Rivers** (1-2): coarse A* preferring downhill, Chaikin smoothing,
   monotone water level (never rises downstream), carved channel
   (half-width 9-16 m) with parabolic sloping banks, flow vectors along path.

3. **Lakes** (1-2): flattest of 40 candidate sites, parabolic bowl carving,
   water level = lowest rim - 0.5 m.

4. **Swamps**: low (below median), flat (slope < 1 m), with noise mask.

5. **Shadows**: baked by marching toward a random sun (elevation 26-56 degrees).

## Determinism

The same seed and configuration always produce the same terrain.
No wall clock, no unseeded randomness, `StrictMath` for trigonometry.

## Build

```bash
./gradlew :bs-terrain:check
```
