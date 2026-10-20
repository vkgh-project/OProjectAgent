# bs-ui

UI module for the BattleSim project. Provides the HUD renderer and unit picker.

## Public API

- **`HudRenderer`** — renders the HUD (heads-up display) from a `SessionView`.
  - `render(SessionView, Optional<UnitView>)` → `HudModel`
  - `render(SessionView, Optional<UnitView>, OrbitCamera, int, int)` → `HudModel` (with NATO markers)
  - `recordOutcome(Outcome)` — tracks round results for totals display
  - `reset()` — clears tracked outcomes

- **`Picker`** — picks a unit from screen coordinates.
  - `pick(x, y, SessionView, OrbitCamera)` → `Optional<UnitView>`
  - Strategy: marker hit-test first, then ray vs unit spheres

## Data Model

All in package `ua.battlesim.ui`:

- `HudModel` — complete HUD output (texts, markers, unit panel, banner, hints)
- `HudText` — a text element with position and styling
- `NatoMarker` — NATO symbol marker above a unit
- `UnitPanel` — selected unit information panel
- `HealthBar` — health bar in the unit panel
- `Banner` — round-result banner
- `KeyHint` — keyboard hint
- `Rect` — screen-space rectangle

## Internal

Package `ua.battlesim.ui.internal`:

- `NatoSymbolRasteriser` — rasterises NATO symbols to a 2D boolean grid (no GPU)
- `LayoutMath` — HUD layout calculations
- `ScreenProjection` — world-to-screen projection and ray unprojection
- `CompassHeading` — heading-to-compass-direction conversion

## Dependencies

- `bs-api` (contracts)
- `bs-gfx-core` (OrbitCamera, JOML)

## Build

```
./gradlew :bs-ui:check
```
