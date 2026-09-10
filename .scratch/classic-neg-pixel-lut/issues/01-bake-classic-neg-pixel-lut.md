# 01: Bake and ship the "Classic Neg. (Pixel)" derived LUT

**What to build:** A new offline Kotlin tool in `:render` (`com.fujivibe.render.tools` package)
that derives `derived-luts/Classic Neg Pixel sRGB.cube` from the existing `Provia to Classic Neg
sRGB.cube` by running every table entry's output RGB through a tone-curve, then split-tone, then
HSL-shift correction (see spec.md's Solution for the exact adjustments and Implementation Decisions
for file/module layout). The derived `.cube` is committed to the repo and wired into both
`:render`'s test resources and `:app`'s main resources, alongside a new `FilmSimulation.CLASSIC_NEG_PIXEL`
entry ("Classic Neg. (Pixel)") ordered right after `CLASSIC_NEG`. No changes to `Cube3DLut`,
`LutRenderPipeline`, `ResourceLutLoader`, or `ReviewCycle` — the new entry flows through unchanged.
Correction constants must be easy to tweak and re-bake (`./gradlew :render:run` after editing a
constant), since the initial values are a starting guess to be tuned visually against real Pixel
photos. Also: new ADR `0005-classic-neg-pixel-derived-variant.md` superseding ADR 0004's "exactly
two" framing, and a small `CONTEXT.md` edit to the "Film Simulation" definition.

**Blocked by:** none

**Status:** implemented, pending visual tuning against real Pixel photos. Built in worktree
`.worktrees/classic-neg-pixel-lut` on branch `classic-neg-pixel-lut`, via TDD
(superpowers:test-driven-development). `:render:test`, `:app:testDebugUnitTest`, and
`:app:assembleDebug` all pass/succeed.

- [x] `LutCorrection.kt` implements tone-curve, split-tone, and HSL-shift as pure `Rgb -> Rgb`
      functions, composed in that order, with tunable constants declared at the top of the file
- [x] `CubeLutWriter.kt` serializes a `Cube3DLut` back to valid `.cube` text
- [x] `BakeClassicNegPixelLut.kt`'s `main()` reads the source conversion LUT, applies the
      correction to every entry, and writes `derived-luts/Classic Neg Pixel sRGB.cube`
- [x] `:render` has the `application` plugin wired to run the bake tool via `./gradlew :render:run`
- [x] `derived-luts/Classic Neg Pixel sRGB.cube` is committed and wired into `:render`'s test
      resources and `:app`'s main resources, mirroring the existing two `.cube` files
- [x] `FilmSimulation.CLASSIC_NEG_PIXEL` exists, ordered directly after `CLASSIC_NEG`, and its kdoc
      no longer claims the fixed set is "exactly" the original two
- [x] Review's swipe cycle includes "Classic Neg. (Pixel)" with zero `ReviewCycle`/UI code changes
      (`ReviewCycleTest` updated for the new 4-entry order, since it hardcoded the old 3-entry cycle)
- [x] Unit tests cover the correction math (black lift, curve monotonicity, split-tone direction,
      hue-shift direction, vibrance reduction) and a `CubeLutWriter` round-trip test
- [x] A `ResourceLutLoaderWiringTest`-style test confirms `CLASSIC_NEG_PIXEL` resolves and loads
- [x] ADR 0005 written, recording the Pixel/Provia baseline-mismatch rationale and superseding ADR
      0004's "exactly two" framing
- [x] `CONTEXT.md`'s "Film Simulation" definition updated to allow FujiVibe-derived variants
- [ ] Visually verified on real Pixel photos against the original "Classic Neg." — not just that it
      builds and loads, but that it actually reads better; correction constants adjusted as needed

## Comments

Code review (mattpocock-skills:code-review, Standards + Spec axes) found and fixed a real bug:
`splitTone`'s highlight offset could push RGB slightly above 1.0, which `rgbToHsl` didn't handle,
collapsing the result to flat white and silently erasing the warm highlight tint (178 of 32768 grid
cells in the baked `.cube` were exactly `1.0 1.0 1.0`). Fixed by clamping `splitTone`'s output to
`[0, 1]`; re-baked, 0 flattened cells confirmed. Standards axis found no hard violations, only minor
judgement calls (a small hue-shift-constants data clump, a couple of composition tests that
re-invoke production code rather than asserting independent values) — left as-is.

Added `PreviewFilmSimulation.kt` (`./gradlew :render:previewFilmSimulation --args="<input> <output>
[PANEL[,PANEL...]]"`, PANEL is ORIGINAL or a FilmSimulation name, default
`ORIGINAL,CLASSIC_NEG_PIXEL`): renders a real photo through any registered `FilmSimulation` (via
the actual `LutRenderPipeline`, not a reimplementation) and writes a multi-panel side-by-side
comparison image, so the correction constants can be tuned by eye against a real Pixel photo
without installing the app on a device. Not part of the original ticket scope — added on request
to support the visual-tuning step above. Also fixed a bug where writing a JPEG output silently
produced nothing (`ImageIO.write` returns `false`, doesn't throw, for an ARGB image against a
format with no alpha channel) — now uses an alpha-free `BufferedImage` for output and checks the
write result.

First on-device-free visual check against a real Pixel photo showed the correction reading as
punchy/warm-pink on near-neutral tones (paper, walls) — clearly visible once put side by side with
plain "Classic Neg." (the base LUT alone stayed subtle; the added correction was overshooting).
The user then supplied a reference image showing the look they actually wanted: uniformly
cool/teal, heavily desaturated, matte-lifted-black, with **no** warm push anywhere — a different
direction than the original text spec's "warm highlights." Retuned `LutCorrection`'s constants to
match: `HIGHLIGHT_TINT` flipped from warm amber to a cool offset, `S_CURVE_STEEPNESS` lowered
2.2→1.6 (per-channel S-curve was adding saturation the reference didn't have),
`GLOBAL_VIBRANCE_REDUCTION` raised 0.12→0.4, `REDS_SAT_BOOST` cut 0.08→0.02. One existing test's
assertion no longer matched the retuned behavior (reds no longer *net* gain saturation once the
0.4 global cut dominates) — rewritten to assert reds retain more saturation than the flat global
cut alone would give them, which still holds. Re-rendered against the same Pixel photo: the
pink/orange overshoot is gone.

Second tuning pass: user liked the direction but flagged it as "way brighter" than intended, and
asked for the cool cast to extend further into midtones (leaving only peak highlights near-neutral)
and an additional 15% saturation cut targeted at yellows/oranges. Implemented all three:
`splitTone`'s shadow/highlight blend changed from a flat linear-by-luma mix to `1 - luma^SHADOW_REACH`
(stays near full shadow-tint strength through midtones, only fading near peak highlights);
added a targeted yellow/orange saturation cut in `hslShift`, applied on top of the global vibrance
reduction; raised `S_CURVE_STEEPNESS` back from 1.6 to 1.9 to restore contrast/punch without
reintroducing the earlier saturation overshoot. User confirmed they like the result.

User then asked to remove the original, unmodified Classic Neg. entirely, keeping only Classic
Neg. (Pixel) — see ADR 0006. `FilmSimulation.CLASSIC_NEG` removed from the registry;
`BakeClassicNegPixelLut` still reads the original `.cube` file directly by path as its source, so
that file stays. `ReviewCycle`, `ResourceLutLoaderWiringTest`, `ReviewCycleTest`,
`PreviewFilmSimulation`'s panel map, and a couple of incidental test references to
`FilmSimulation.CLASSIC_NEG` all updated accordingly. `:render:test`, `:app:testDebugUnitTest`, and
`:app:assembleDebug` all pass after the removal.

Still pending: eventual on-device verification.
