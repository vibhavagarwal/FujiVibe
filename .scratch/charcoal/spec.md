# Charcoal Spec: the black-and-white look

## Problem Statement
The user wants a black-and-white look in FujiVibe, based on Kevin Mullins' Fujifilm black-and-white recipe
(Acros + Ye, 1970s/80s press photography): https://www.kevinmullinsphotography.co.uk/blog/fujifilm-recipe-black-and-white
No Acros LUT source exists in the pack (ADR 0003).

## Solution
Bake a derived `Charcoal sRGB.cube` offline (see ADR 0011) directly from `CharcoalCorrection`: slight WB gains, a
yellow-filter monochrome conversion, then a hard S-curve. No source `.cube`. Registered as `FilmSimulation.CHARCOAL`,
last in the swipe order, with its own stronger and finer `GrainProfile`.

Recipe: Acros + Ye; Grain Strong/Small; WB Shift R-4/B-3; Highlight +4; Shadow +4; Sharpness +2; Clarity 0.

## Implementation Decisions
- `CharcoalCorrection.kt`, `BakeCharcoalLut.kt` in `com.fujivibe.render.tools`; `bakeCharcoalLut` Gradle task.
- `FilmSimulation.CHARCOAL`; `GrainRenderPipeline` gains per-look `GrainProfile`s (`GRAIN_PROFILES`), with
  `GRAIN_ENABLED_SIMULATIONS` derived from its keys. The other looks keep the default profile.
- Added to `PreviewFilmSimulation`'s LUT map and the test classpath's LUT include list.
- Re-bake after tweaking constants: `./gradlew :render:bakeCharcoalLut`; preview:
  `./gradlew :render:previewFilmSimulation --args="<in> <out> ORIGINAL,CHARCOAL"`.

## Out of Scope
Sharpness +2 and Clarity (spatial); Pixel-specific compensation; low-light tuning.
