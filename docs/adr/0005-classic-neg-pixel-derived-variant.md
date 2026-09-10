---
status: accepted
---

# Classic Neg. (Pixel): a FujiVibe-derived variant, superseding ADR 0004's "exactly two"

Testing Classic Neg. against real Pixel-phone JPEGs (rather than Fuji X-camera output) showed the
look reading as flat/off. `Provia to Classic Neg sRGB.cube` is a *conversion* LUT — it encodes the
delta between Provia's color science and Classic Neg's, not a full look built from scratch. Its own
README states it will "convert images processed with Provia" and can "also be applied directly to
camera jpegs," which assumes a generic camera JPEG's baseline rendering is close enough to Provia's.
A Pixel's JPEG comes out of Google's HDR+/computational-photography pipeline, which lifts shadows
and flattens local contrast far more aggressively than Provia's rendering does — a baseline-color-
science mismatch, distinct from (and more severe than) the gamma/linear-input concern ADR 0004
already covers.

There's no deterministic way to undo a Pixel's adaptive, per-scene HDR+ processing before the LUT
runs (it isn't a fixed, published transform), so the fix is a compensating correction applied to the
LUT's output, tuned by eye against real photos, not a colorimetric pre-transform.

**Decision:** ship a second, FujiVibe-authored Film Simulation, `CLASSIC_NEG_PIXEL` ("Classic Neg.
(Pixel)"), alongside the untouched original `CLASSIC_NEG`. Its `.cube` is a derived composite: the
original conversion LUT's output run through three additional pointwise corrections — a tone curve,
split-tone, and HSL shift (see `com.fujivibe.render.tools.LutCorrection` and
`.scratch/classic-neg-pixel-lut/spec.md` for the exact adjustments) — baked together into one new
`.cube` file offline, via `BakeClassicNegPixelLut` (`./gradlew :render:run`). Because every
correction is pointwise, it composes losslessly with the existing LUT into a single grid of the same
size: the runtime render pipeline (`Cube3DLut`, `LutRenderPipeline`, `ResourceLutLoader`) needs no
changes, and `ReviewCycle` picks up the new entry automatically since it already derives its swipe
order from `FilmSimulation.entries`.

The derived `.cube` lives under a new, **committed** `derived-luts/` directory — unlike `raw-assets/`
(gitignored, license-gated per ADR 0003), this file is FujiVibe's own derivative work, not the
redistributable third-party pack itself.

This supersedes ADR 0004's "exactly Classic Neg. and Nostalgic Neg." framing: the launch set is no
longer fixed at two, and FujiVibe may ship its own derived variants of a stock Fuji look when the
stock look needs compensating for how this app's Capture differs from a Fuji camera's JPEG.

**Explicitly deferred**, not decided against:
- The same Pixel-compensation treatment for Nostalgic Neg. — same pattern, different source LUT and
  likely different tuning, tracked as a separate follow-up.
- Film grain, the fourth correction originally proposed alongside the tone curve/split-tone/HSL
  shift: a static 3D LUT is a pure function of input RGB and can't express per-pixel randomness or
  spatial texture, so it would need a genuine new pipeline stage rather than a LUT bake.
- The initial correction constants are an informed starting guess, not a measured fit against real
  Provia-vs-Pixel-JPEG output; they're expected to be tuned visually via the re-bake loop.
