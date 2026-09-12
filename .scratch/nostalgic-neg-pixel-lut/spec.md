# Nostalgic Neg. (Pixel) Spec

## Problem Statement

Same underlying issue as Classic Neg. (see ADR 0005, `.scratch/classic-neg-pixel-lut/spec.md`):
`Provia to Nostalgic Neg sRGB.cube` (ADR 0003/0004) is a *conversion* LUT encoding the delta between
Provia's and Nostalgic Neg's color science, not a full look built from scratch. It assumes a
Provia-like camera baseline. A Pixel phone's JPEG comes out of Google's HDR+ pipeline instead, which
diverges from that baseline — the same class of mismatch that made plain Classic Neg. read as
flat/off on Pixel photos. That prior ticket explicitly deferred "the same treatment for Nostalgic
Neg." as its own follow-up (see spec.md's Out of Scope and ADR 0005's deferred list) — this is that
follow-up.

The user supplied a reference image (a real Pixel photo run through a Fuji "Nostalgic Negative"
simulation elsewhere) showing the target look: warm/amber cast overall, milky lifted blacks with a
faint reddish-brown undertone, compressed highlights (no stark digital white), blues/greens
desaturated, reds/oranges kept rich and saturated, plus fine luminance film grain.

## Solution

Ship a new FujiVibe-authored Film Simulation, **Nostalgic Neg. (Pixel)**, alongside the existing
untouched **Nostalgic Neg.** (kept for now — removal, if it happens, is a separate later decision,
same sequencing as Classic Neg.'s original/Pixel split). Its `.cube` is a derived composite: the
original conversion LUT's output run through three pointwise corrections and baked into one new
`.cube` file offline, using the same architecture as Classic Neg. (Pixel) — a new, independent
correction-constants object rather than a shared/parameterized one, since the color direction here is
essentially inverted (warm push vs. Classic Neg Pixel's cool push) and independent tuning mattered a
lot last time.

The three corrections, applied in this order to every table entry's output RGB (starting-guess
constants — expected to be retuned by eye against real Pixel photos, same as last time):

1. **Tone curve** — black point lifted for a milky, faded-black look; highlight-side steepness < 1
   to compress the white point and soften stark highlights (same shadow/highlight-split S-curve
   mechanism `LutCorrection` already uses, retuned for this look, implemented independently in
   `NostalgicNegCorrection`).
2. **Split-tone** — a warm amber/cream offset blended across *both* shadows and highlights (not
   faded out toward highlights the way Classic Neg Pixel's cool tint is) — this is the global
   white-balance warm push — plus a reddish-brown undertone specifically in the lifted shadows.
3. **HSL shift** — blues and greens desaturated (targeted hue-band weighting, same mechanism as the
   existing red/blue targeting in `LutCorrection`, aimed at different hues); reds/oranges keep full
   saturation or get a small boost, so a red logo/accent stays rich against the faded rest of the
   frame. No blanket global-vibrance cut the way Classic Neg Pixel has one — desaturation here is
   selective (cool hues only), not universal.

**Film grain** is included from the start this time (unlike Classic Neg. (Pixel), which added it in
a later round): `GrainRenderPipeline`/`NoiseGrid` already exist and are designed to extend via the
`GRAIN_ENABLED_SIMULATIONS` set — `NOSTALGIC_NEG_PIXEL` is simply added to that set, no new grain
code.

## Implementation Decisions

- **`NostalgicNegCorrection.kt`** (new file, `com.fujivibe.render.tools`, mirroring
  `LutCorrection.kt`'s shape) — pure `Rgb -> Rgb` functions for the tone curve, split-tone, and HSL
  shift, composed into `correct(Rgb): Rgb`. Its own tunable constants, independent of
  `LutCorrection`'s.
- **`BakeNostalgicNegPixelLut.kt`** (new file) — `main()`: reads `Provia to Nostalgic Neg sRGB.cube`
  from the raw third-party pack, applies `NostalgicNegCorrection.correct` to every entry, writes via
  the existing (reused as-is) `CubeLutWriter` to `derived-luts/Nostalgic Neg Pixel sRGB.cube`.
- **`render/build.gradle.kts`**: register a new `bakeNostalgicNegPixelLut` `JavaExec` task (mirroring
  the existing `previewFilmSimulation` task's shape — a registered task, not repurposing the
  `application` block's `mainClass`/`run`, since that stays Classic Neg Pixel's entrypoint). Re-baking
  after a constant tweak is `./gradlew :render:bakeNostalgicNegPixelLut`. Add
  `Nostalgic Neg Pixel sRGB.cube` to `:render`'s test resources include list alongside the existing
  two.
- **New `derived-luts/Nostalgic Neg Pixel sRGB.cube`**, committed (FujiVibe's own derivative, same
  as the Classic Neg Pixel file).
- **`app/build.gradle.kts`**: no change needed — `derived-luts/` is already wired as a `main`
  resources `srcDir`; the new file is picked up automatically.
- **`FilmSimulation.kt`**: add `NOSTALGIC_NEG_PIXEL("Nostalgic Neg. (Pixel)", "Nostalgic Neg Pixel
  sRGB.cube")`, ordered directly after `NOSTALGIC_NEG` (adjacent, for swipe comparison). Update kdoc.
- **`GrainRenderPipeline.kt`**: add `FilmSimulation.NOSTALGIC_NEG_PIXEL` to
  `GRAIN_ENABLED_SIMULATIONS`.
- **`PreviewFilmSimulation.kt`**: add `FilmSimulation.NOSTALGIC_NEG_PIXEL to File("derived-luts")`
  to its `LUT_DIRECTORIES` map (mirroring `CLASSIC_NEG_PIXEL`'s entry) — the map is keyed per
  `FilmSimulation` and doesn't populate itself from `FilmSimulation.entries`, so a new entry needs
  an explicit line here even though the panel-name parsing itself needs no change.
- **New ADR** `docs/adr/0007-nostalgic-neg-pixel-derived-variant.md`: records the same rationale
  pattern as ADR 0005, notes it's the deferred follow-up ADR 0005 called out.
- **`CONTEXT.md`**: no change needed — already generalized to allow FujiVibe-derived variants by
  ADR 0005's ticket.

## Testing Decisions

Same shape as the Classic Neg. (Pixel) ticket: `NostalgicNegCorrection`'s pure `Rgb -> Rgb` logic gets
real unit test coverage — black lift produces a milky, warm-tinted (not neutral-gray) near-black;
tone curve stays monotonic; highlight steepness compresses (pulls down) rather than boosts; split-tone
pushes both shadows and highlights warm (not faded to neutral at the top, unlike Classic Neg Pixel);
blue/green hue bands lose saturation; red/orange hue bands retain or gain saturation. `CubeLutWriter`
is reused as-is — no new writer test needed. A `ResourceLutLoaderWiringTest`-style test confirms
`NOSTALGIC_NEG_PIXEL` resolves and loads. `ReviewCycleTest` updated for the new 5-entry swipe order.
`GrainRenderPipelineTest` extended to confirm `NOSTALGIC_NEG_PIXEL` is in the enabled grain set.

Whether the resulting *look* is right is inherently visual, same caveat as before: the tune/re-bake
loop (`./gradlew :render:bakeNostalgicNegPixelLut`, then
`./gradlew :render:previewFilmSimulation --args="<input> <output> NOSTALGIC_NEG,NOSTALGIC_NEG_PIXEL"`)
is how constants get dialed in against the user's reference photo, then a final on-device pass.

## Out of Scope

- Removing the original, unmodified Nostalgic Neg. from the registry — a separate later decision,
  not part of this ticket (see ADR 0006's precedent for Classic Neg., which followed the same
  keep-both-then-decide sequencing).
- Getting the correction constants right on the first pass — starting guesses, tuned visually after
  implementation via the re-bake loop, same as Classic Neg. (Pixel).
- Sharing/parameterizing the correction machinery between `LutCorrection` and
  `NostalgicNegCorrection` — kept independent per this ticket's design decision.
