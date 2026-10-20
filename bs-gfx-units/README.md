# bs-gfx-units

Battle units renderer for the Battle Sim project (Layer 3). Depends only on
`bs-api` (contracts) and `bs-gfx-core` (rendering core).

## Public API

| Class | Description |
|-------|-------------|
| `ua.battlesim.gfx.units.UnitRenderer` | Renders the units of a `RenderSnapshot`: `render(RenderSnapshot, double alpha, OrbitCamera, Optional<Integer> selectedId)` |

## Procedural models

- **Tank** — hull, two tracks with a scrolling tread, four road wheels, a
  turret and a gun.
- **APC infantry** — hull, eight wheels, a turret and three seated soldiers
  (torso, vest, head).
- **Infantry** — a squad of four soldiers, each with two walking legs, a torso,
  a vest and a head.

## Animation

All animation is derived deterministically from the snapshot (no wall clock, no
unseeded randomness; `StrictMath` for trigonometry):

- position and heading are **interpolated with `alpha`** between the previous
  and current frame values;
- the model **tilts to the terrain** (its up axis aligns with the surface
  normal, its forward follows the heading on the tangent plane);
- **wheels spin** and **legs swing** from the odometer; tank tracks show a
  scrolling tread;
- the **tank gun** points along the heading while moving and swings toward the
  target while attacking, with a deterministic **recoil** pulse;
- **destroyed** units are charred and tipped over;
- **soft blob shadows** are drawn under each unit (a bright ring marks the
  selected unit);
- a **strong ambient fill** keeps the models readable in any lighting.

## Build

```
./gradlew :bs-gfx-units:check
```

## Demo

```
./gradlew :bs-gfx-units:run
```

Opens a window with a small scene of tanks, an APC and an infantry squad on a
gently sloping terrain, animating the interpolation and the unit animations.
(macOS requires `-XstartOnFirstThread`.)

## Tests

The unit tests run **without a GPU** and cover:

- the **animation maths** — angle lerp (shortest arc + wrap-around), rotation
  primitives, the local-to-world tilt matrix, wheel/tread/gun/recoil/leg values
  (`UnitMathTest`);
- the **model builders** — piece and role counts, deterministic vertex counts
  and NaN-free geometry for all three unit types (`ModelBuildersTest`);
- **pose computation** from a `UnitView` — position/heading interpolation,
  gun aim, recoil, charred/lying, selection and side paint (`UnitPoseTest`);
- the **vertex writer** — vertex counts, selection marker gating and finite
  output (`UnitVertexWriterTest`);
- **paint** — side colours, vest brightness, charred darkening, shadow alpha
  (`PaintTest`);
- the **renderer** contract — argument validation and safe disposal, without
  touching OpenGL (`UnitRendererTest`).
