# Koda64 (Pixel) Spec: the Kodachrome 64 recipe

## Problem Statement
The user wants the fujixweekly "Kodachrome 64" film simulation recipe (Classic Chrome base, X-T5) as a new look in
FujiVibe. The pack's Classic Chrome LUT expects linear input (ADR 0004), so it can't be applied to a JPEG directly.

## Solution
Bake a derived `Koda64 Pixel sRGB.cube` offline (see ADR 0010): linearize input, divide the LUT's built-in tone curve
mostly back out, then apply `Koda64PixelCorrection` (warm WB, deeper shadows, highlight roll-off, Color/Color Chrome,
blue deepening, skin lightening). Registered as `FilmSimulation.KODA64_PIXEL`, last in the swipe order, with grain.

## Implementation Decisions
- `Koda64PixelCorrection.kt`, `BakeKoda64PixelLut.kt` in `com.fujivibe.render.tools`; `bakeKoda64PixelLut` Gradle task.
- `FilmSimulation.KODA64_PIXEL`; added to `GRAIN_ENABLED_SIMULATIONS` and `PreviewFilmSimulation`'s LUT map.
- Re-bake after tweaking constants: `./gradlew :render:bakeKoda64PixelLut`; preview:
  `./gradlew :render:previewFilmSimulation --args="<in> <out> ORIGINAL,KODA64_PIXEL"`.

## Out of Scope
Clarity +3; Pixel-specific compensation; low-light tuning.
