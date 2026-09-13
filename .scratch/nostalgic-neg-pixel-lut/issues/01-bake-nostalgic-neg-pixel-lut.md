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

**Status:** done. Implemented, visually tuned against real Pixel photos (two rounds), original
unmodified Nostalgic Neg. removed from the registry per ADR 0008, and confirmed on-device.

- [x] `NostalgicNegCorrection.kt` implements tone-curve, split-tone, and HSL-shift as pure
      `Rgb -> Rgb` functions, composed in that order, with tunable constants declared at the top of
      the file — independent of `LutCorrection`'s constants
- [x] `BakeNostalgicNegPixelLut.kt`'s `main()` reads the source conversion LUT, applies the
      correction to every entry, and writes `derived-luts/Nostalgic Neg Pixel sRGB.cube` (reusing
      the existing `CubeLutWriter`)
- [x] `:render`'s `build.gradle.kts` registers a new `bakeNostalgicNegPixelLut` `JavaExec` task
      (mirroring `previewFilmSimulation`'s shape), so re-baking is
      `./gradlew :render:bakeNostalgicNegPixelLut`
- [x] `derived-luts/Nostalgic Neg Pixel sRGB.cube` is committed and wired into `:render`'s test
      resources, mirroring the existing entries
- [x] `FilmSimulation.NOSTALGIC_NEG_PIXEL` exists, ordered directly after `NOSTALGIC_NEG`
- [x] `PreviewFilmSimulation.kt`'s `LUT_DIRECTORIES` map includes `NOSTALGIC_NEG_PIXEL -> derived-luts/`
- [x] Review's swipe cycle includes "Nostalgic Neg. (Pixel)" with zero `ReviewCycle`/UI code changes
      (`ReviewCycleTest` updated for the new 5-entry order)
- [x] `GrainRenderPipeline`'s `GRAIN_ENABLED_SIMULATIONS` set includes `NOSTALGIC_NEG_PIXEL`
      (`GrainRenderPipelineTest` updated)
- [x] Unit tests cover the correction math (warm-tinted black lift, curve monotonicity, highlight
      compression direction, warm split-tone direction on both shadows and highlights, blue/green
      desaturation direction, red/orange saturation retained or boosted)
- [x] A `ResourceLutLoaderWiringTest`-style test confirms `NOSTALGIC_NEG_PIXEL` resolves and loads
- [x] ADR 0007 written, recording the same-pattern rationale as ADR 0005
- [x] Visually verified on real Pixel photos against the user's reference image — not just that it
      builds and loads, but that it actually reads as intended; correction constants adjusted as
      needed via `./gradlew :render:previewFilmSimulation --args="<input> <output>
      ORIGINAL,NOSTALGIC_NEG_PIXEL"`
- [x] On-device verification (adb install, real Pixel phone)
- [x] Original, unmodified `NOSTALGIC_NEG` removed from the registry once the Pixel variant was
      confirmed (see ADR 0008) — registry is now exactly `CLASSIC_NEG_PIXEL`, `NOSTALGIC_NEG_PIXEL`

## Comments

Tasks 1-6 of the implementation plan (`docs/superpowers/plans/2026-09-12-nostalgic-neg-pixel-lut.md`)
are complete, each independently reviewed with no Critical/Important findings. A final
whole-branch review found no blocking issues, only a small cleanup pass (stale ticket status, a
tautological test, and two stale doc/test strings) addressed as a follow-up commit.

First visual tuning pass (real Pixel photos, one indoor/one outdoor) showed the correction reading
as a blanket global white-balance shift rather than a graded look: skin tones excessively orange,
foliage turned to "yellow mud," white paper/plastic washed out to sepia — the warm highlight tint
was reaching all the way to the white point, and `SPLIT_TONE_SHADOW_REACH` (2f) kept shadow-tint
weight high well into midtones. Retuned: `HIGHLIGHT_TINT` zeroed (highlights/white point now stay
clean), `SPLIT_TONE_SHADOW_REACH` dropped 2f → 0.6f (confines warmth to shadows/lower-midtones,
fading out well before highlights), `GREENS_SAT_REDUCTION`/`BLUES_SAT_REDUCTION` cut 0.45 → 0.28
(muted, not muddied), `REDS_ORANGES_SAT_BOOST` raised 0.10 → 0.18 (reds read as deliberately rich).
Re-rendered against both photos: highlights/whites stayed clean, foliage stayed green, skin read
warm but natural, reds stayed vivid. User confirmed this round looks right.

User then asked to remove the original, unmodified Nostalgic Neg. entirely, keeping only Nostalgic
Neg. (Pixel) — see ADR 0008, same sequencing as ADR 0006's Classic Neg. removal.
`FilmSimulation.NOSTALGIC_NEG` removed from the registry; `GrainRenderPipeline`,
`PreviewFilmSimulation`'s `LUT_DIRECTORIES` map, `render/build.gradle.kts`'s test-resources include
list, `ResourceLutLoaderWiringTest`, `GrainRenderPipelineTest`, and `ReviewCycleTest` all updated
accordingly (the registry is now exactly `CLASSIC_NEG_PIXEL`, `NOSTALGIC_NEG_PIXEL` — both
FujiVibe-derived Pixel variants, no stock pass-through entries left). `GrainRenderPipelineTest`'s
"not in the grain-enabled set" test was replaced with an assertion that every currently registered
Film Simulation is grain-enabled, since removing `NOSTALGIC_NEG` left no real registry member
outside that set to test the exclusion branch against. `:render:test`, `:app:testDebugUnitTest`,
and `:app:assembleDebug` all pass after the removal.

On-device install (adb, real Pixel phone) confirmed: swipe cycle now shows only Original → Classic
Neg. (Pixel) → Nostalgic Neg. (Pixel), and the Nostalgic Neg. (Pixel) look (warm shadows/lower
midtones, clean highlights, muted-not-muddy greens/blues, rich reds, grain) reads correctly. Ticket
complete.
