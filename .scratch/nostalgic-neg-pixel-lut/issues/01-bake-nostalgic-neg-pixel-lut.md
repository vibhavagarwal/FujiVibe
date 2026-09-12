# 01: Bake and ship the "Nostalgic Neg. (Pixel)" derived LUT

**What to build:** A new offline Kotlin tool in `:render` (`com.fujivibe.render.tools` package)
that derives `derived-luts/Nostalgic Neg Pixel sRGB.cube` from the existing `Provia to Nostalgic Neg
sRGB.cube` by running every table entry's output RGB through a tone-curve, then split-tone, then
HSL-shift correction (see spec.md's Solution for the exact adjustments and Implementation Decisions
for file/module layout) — a new, independent `NostalgicNegCorrection` object, not shared with
`LutCorrection`. The derived `.cube` is committed to the repo and wired into both `:render`'s test
resources and `:app`'s main resources, alongside a new `FilmSimulation.NOSTALGIC_NEG_PIXEL` entry
("Nostalgic Neg. (Pixel)") ordered right after `NOSTALGIC_NEG`. Film grain is included from the
start via `GrainRenderPipeline`'s existing `GRAIN_ENABLED_SIMULATIONS` set. No changes to
`Cube3DLut`, `LutRenderPipeline`, `ResourceLutLoader`, or `ReviewCycle`. Also: new ADR
`0007-nostalgic-neg-pixel-derived-variant.md`.

**Blocked by:** none

**Status:** not started.

- [ ] `NostalgicNegCorrection.kt` implements tone-curve, split-tone, and HSL-shift as pure
      `Rgb -> Rgb` functions, composed in that order, with tunable constants declared at the top of
      the file — independent of `LutCorrection`'s constants
- [ ] `BakeNostalgicNegPixelLut.kt`'s `main()` reads the source conversion LUT, applies the
      correction to every entry, and writes `derived-luts/Nostalgic Neg Pixel sRGB.cube` (reusing
      the existing `CubeLutWriter`)
- [ ] `:render`'s `build.gradle.kts` registers a new `bakeNostalgicNegPixelLut` `JavaExec` task
      (mirroring `previewFilmSimulation`'s shape), so re-baking is
      `./gradlew :render:bakeNostalgicNegPixelLut`
- [ ] `derived-luts/Nostalgic Neg Pixel sRGB.cube` is committed and wired into `:render`'s test
      resources, mirroring the existing entries
- [ ] `FilmSimulation.NOSTALGIC_NEG_PIXEL` exists, ordered directly after `NOSTALGIC_NEG`
- [ ] `PreviewFilmSimulation.kt`'s `LUT_DIRECTORIES` map includes `NOSTALGIC_NEG_PIXEL -> derived-luts/`
- [ ] Review's swipe cycle includes "Nostalgic Neg. (Pixel)" with zero `ReviewCycle`/UI code changes
      (`ReviewCycleTest` updated for the new 5-entry order)
- [ ] `GrainRenderPipeline`'s `GRAIN_ENABLED_SIMULATIONS` set includes `NOSTALGIC_NEG_PIXEL`
      (`GrainRenderPipelineTest` updated)
- [ ] Unit tests cover the correction math (warm-tinted black lift, curve monotonicity, highlight
      compression direction, warm split-tone direction on both shadows and highlights, blue/green
      desaturation direction, red/orange saturation retained or boosted)
- [ ] A `ResourceLutLoaderWiringTest`-style test confirms `NOSTALGIC_NEG_PIXEL` resolves and loads
- [ ] ADR 0007 written, recording the same-pattern rationale as ADR 0005
- [ ] Visually verified on real Pixel photos against the user's reference image — not just that it
      builds and loads, but that it actually reads as intended; correction constants adjusted as
      needed via `./gradlew :render:previewFilmSimulation --args="<input> <output>
      NOSTALGIC_NEG,NOSTALGIC_NEG_PIXEL"`
- [ ] On-device verification (adb install, real Pixel phone)

## Comments

(none yet)
