# Classic Neg. (Pixel) Spec

## Problem Statement

Testing "Classic Neg." against Pixel-phone JPEGs (rather than genuine Fuji X-camera output) showed
the look reading as flat/off compared to what the same LUT produces on its intended input.

`Provia to Classic Neg sRGB.cube` (see ADR 0003/0004) is a *conversion* LUT: it encodes the delta
between Provia's color science and Classic Neg's, not a full look built from scratch. Its own
README states it will "convert images processed with Provia" and can "also be applied directly to
camera jpegs" — an assumption that a generic camera JPEG's baseline rendering is close enough to
Provia's. A Pixel's JPEG comes out of Google's HDR+/computational-photography pipeline, which lifts
shadows and flattens local contrast far more aggressively than Provia's rendering does. That's a
different, and more severe, mismatch than the gamma/linear concern ADR 0004 already covers — it's a
baseline-color-science mismatch, not an encoding mismatch — and is a plausible cause of the look
reading as off.

There's no deterministic way to undo a Pixel's adaptive, per-scene HDR+ processing before the LUT
runs (it isn't a fixed, published transform), so the fix has to be a compensating correction applied
after the fact, tuned by eye against real output, not a colorimetric pre-transform.

## Solution

Ship a second, FujiVibe-authored Film Simulation, **Classic Neg. (Pixel)**, alongside the untouched
original **Classic Neg.** Its `.cube` is a derived composite: the original conversion LUT's output,
run through three additional pointwise corrections and baked into one new `.cube` file offline.
Because every correction is pointwise (each output RGB depends only on the corresponding input RGB),
it composes losslessly with the existing LUT into a single grid of the same size — the runtime
render pipeline (`Cube3DLut`, `LutRenderPipeline`, `ResourceLutLoader`) needs no changes; it's simply
handed one more `.cube` resource, exactly like the two it already loads. `ReviewCycle` already
derives its swipe order from `FilmSimulation.entries`, so the new entry appears in Review
automatically with no UI code changes.

The three corrections, applied in this order to every table entry's output RGB:

1. **Tone curve** — steep S-curve on the RGB master curve to compress midtones; black point lifted
   ~3-5%; shadows in the 10-25% range pulled down aggressively for a heavier rolloff.
2. **Split-tone** — a cool cyan-green offset in shadows, a warm amber/cream offset in highlights,
   blended by approximate luminance.
3. **HSL shift** — reds rotated toward magenta/brick with lowered luminance and raised saturation;
   blues/cyans rotated toward dark teal; global vibrance reduced ~10-15%.

A fourth item originally proposed — film grain — is excluded: a static 3D LUT is a pure function of
input RGB and can't express per-pixel randomness or spatial texture. Adding it would require a real
new pipeline stage. Deferred (see Out of Scope).

The same treatment for **Nostalgic Neg.** is a deliberate, separate follow-up — same pattern,
different source LUT, likely different tuning — not part of this feature.

## Implementation Decisions

- **New package `com.fujivibe.render.tools`** in the existing `:render` module (not a new Gradle
  module — kept simple; the tool's pure-math code is negligible bundled weight, and a new module
  would be more Gradle plumbing than this warrants):
  - `LutCorrection.kt` — pure `Rgb -> Rgb` functions for the tone curve, split-tone, and HSL shift,
    composed into a single `correct(Rgb): Rgb`. Tunable constants (black-point lift %, shadow
    rolloff range/strength, shadow/highlight split-tone RGB offsets, red/blue hue-target angles and
    shift amounts, global vibrance reduction %) are declared at the top of this file.
  - `CubeLutWriter.kt` — serializes a `Cube3DLut` back to `.cube` text, mirroring
    `CubeLutParser`'s format (companion piece; no writer exists today).
  - `BakeClassicNegPixelLut.kt` — `main()`: reads `Provia to Classic Neg sRGB.cube` from the raw
    third-party pack (file path, not classpath — this runs standalone, before the derived file
    exists), applies `LutCorrection.correct` to every table entry, writes the result via
    `CubeLutWriter` to `derived-luts/Classic Neg Pixel sRGB.cube`.
- **`render/build.gradle.kts`**: add the `application` plugin with `mainClass` pointing at
  `BakeClassicNegPixelLut`, so re-baking after a constant tweak is `./gradlew :render:run`. Also add
  `derived-luts/` as a `test` resources dir (mirrors the existing pattern for the two originals).
- **New top-level `derived-luts/` directory**, committed to the repo (unlike `raw-assets/`, which
  stays gitignored/license-gated) — contains `Classic Neg Pixel sRGB.cube`, our own derivative.
- **`app/build.gradle.kts`**: add `derived-luts/` as another `main` resources `srcDir`, so the
  running app can load it exactly like the two originals.
- **`FilmSimulation.kt`**: add `CLASSIC_NEG_PIXEL("Classic Neg. (Pixel)", "Classic Neg Pixel
  sRGB.cube")`, ordered directly after `CLASSIC_NEG` (so the two sit adjacent in the Review swipe
  cycle for easy comparison). Update the enum's kdoc, which currently claims "exactly Classic Neg.
  and Nostalgic Neg." per ADR 0004 — that framing is superseded (see ADR below).
- **New ADR** `docs/adr/0005-classic-neg-pixel-derived-variant.md`: records the Pixel/Provia
  baseline-mismatch rationale, that this ships as a static composite LUT with no pipeline change,
  that it supersedes ADR 0004's "exactly two" framing, and explicitly defers (a) the same treatment
  for Nostalgic Neg., (b) film grain.
- **`CONTEXT.md`**: light edit to the "Film Simulation" definition so it no longer implies every
  entry must be a stock, named-by-Fuji look — FujiVibe may ship its own derived variant when a stock
  look needs compensating for how this app's Capture differs from a Fuji camera's JPEG.

## Testing Decisions

The correction math (tone curve, split-tone, HSL shift) is pure, isolated `Rgb -> Rgb` logic, so it
gets real unit test coverage: black lifts off true black by the configured amount, the tone curve
stays monotonic, split-tone pushes shadows/highlights in the expected direction, hue shift rotates
reds toward magenta (not away from it) and blues toward teal, vibrance reduction actually reduces
saturation. `CubeLutWriter` gets a round-trip test (parse → write → parse is the identity). A wiring
test for `FilmSimulation.CLASSIC_NEG_PIXEL` follows the existing `ResourceLutLoaderWiringTest`
pattern.

Whether the resulting *look* is actually right is inherently visual and out of reach for automated
tests — that's what the tune/re-bake loop is for: adjust a constant, `./gradlew :render:run`,
re-install, compare against real Pixel photos.

## Out of Scope

- Film grain — needs a genuine new pipeline stage (per-pixel randomness a static LUT can't express).
- Applying the same Pixel-compensation treatment to Nostalgic Neg. — separate follow-up ticket.
- Getting the correction constants right on the first pass — they start as an informed guess (see
  Solution) and are expected to be tuned visually after implementation, via the re-bake loop.
