---
status: accepted
---

# Nostalgic Neg. (Pixel): the deferred Pixel-compensation follow-up for Nostalgic Neg.

ADR 0005 shipped `CLASSIC_NEG_PIXEL` to compensate for Pixel-phone JPEGs (Google's HDR+ pipeline)
diverging from the Provia-like baseline the `abpy` conversion LUTs assume, and explicitly deferred
applying the same treatment to Nostalgic Neg. as a separate follow-up. This is that follow-up.

`Provia to Nostalgic Neg sRGB.cube` has the same conversion-LUT structure and the same baseline
assumption as the Classic Neg. LUT did, so the same fix shape applies: a compensating correction
baked into a derived `.cube`, tuned by eye against real Pixel photos, not a colorimetric
pre-transform (there's still no deterministic way to undo HDR+'s adaptive per-scene processing).

**Decision:** ship `NOSTALGIC_NEG_PIXEL` ("Nostalgic Neg. (Pixel)"), alongside the untouched
`NOSTALGIC_NEG`, following ADR 0005's architecture exactly — a derived composite `.cube`, no runtime
pipeline change (`Cube3DLut`, `LutRenderPipeline`, `ResourceLutLoader` untouched;
`ReviewCycle` picks up the new entry automatically via `FilmSimulation.entries`). The correction is a
new, independent `NostalgicNegCorrection` object (tone curve, split-tone, HSL shift — see
`.scratch/nostalgic-neg-pixel-lut/spec.md`), not a shared/parameterized version of `LutCorrection`:
the target look here is a warm push (amber white balance, warm-tinted lifted blacks, compressed
highlights, cool-hue desaturation with reds/oranges protected), essentially inverted from Classic Neg
Pixel's cool/muted direction, so independent tuning knobs matter the same way they did last time.

Unlike Classic Neg. (Pixel), film grain is included from this ticket's start rather than added in a
later round: `GrainRenderPipeline`/`NoiseGrid` already exist for exactly this purpose (the
`GRAIN_ENABLED_SIMULATIONS` set was deliberately built as a set, not a single hardcoded check,
anticipating this). `NOSTALGIC_NEG_PIXEL` is simply added to that set.

**Explicitly deferred**, not decided against:
- Removing the original, unmodified Nostalgic Neg. from the registry — mirrors ADR 0006's precedent
  for Classic Neg., where removal was a separate decision made only after the Pixel variant was
  proven out. Kept alongside for now per the user's direction on this ticket.
- The initial correction constants are an informed starting guess against a single reference image,
  not a measured fit; expected to be tuned visually via the re-bake loop.
