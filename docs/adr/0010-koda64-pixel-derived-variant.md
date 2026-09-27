---
status: accepted
---

# Koda64 (Pixel), the Kodachrome 64 recipe: a derived variant built on the linear-input Classic Chrome LUT

The Kodachrome 64 recipe (fujixweekly, X-T5) is Classic Chrome plus in-camera settings: Grain Weak/Small,
Color Chrome Effect Strong, WB Daylight +2 Red/-5 Blue, DR200, Shadow +0.5, Color +2, Clarity +3.
FujiVibe's engine is a single baked `.cube` (ADR 0001), and ADR 0004 shelved the pack's Classic Chrome LUT
because it expects linear input, not a gamma-encoded JPEG.

**Decision:** ship `KODA64_PIXEL` ("Koda64 (Pixel)", the user's name for the Kodachrome 64 recipe look) as a FujiVibe-derived Film Simulation, keeping the runtime
pipeline unchanged (`Cube3DLut`, `LutRenderPipeline`, `ResourceLutLoader` untouched). The derived
`derived-luts/Koda64 Pixel sRGB.cube` is baked offline by `BakeKoda64PixelLut`:

1. each grid point's sRGB-encoded coordinate is linearized before sampling `classic chrome_sRGB.cube`
   (the pre-transform ADR 0004 wanted, paid for offline, not at runtime);
2. the source LUT's built-in raw-to-display tone curve is largely divided back out via its neutral axis
   (`CLASSIC_CHROME_TONE_KEEP` = 0.35 keeps a third of its contrast). Without this step, output was far too dark with
   hue shifts, because a JPEG is already tone-mapped and the curve was applied twice;
3. `Koda64PixelCorrection` approximates the recipe's settings as pointwise corrections: warm channel gains (WB),
   shadow gamma + highlight shoulder (Shadow/DR200), saturation boost + saturated-color densification
   (Color/Color Chrome), a cyan-blue deepening step (Classic Chrome pushes skies teal), and a skin-tone pass;
4. grain reuses `GrainRenderPipeline`'s `GRAIN_ENABLED_SIMULATIONS` set.

Tuned by eye against five real photos (landscape, portrait, beach, indoor). User feedback after the first pass:
landscapes approved; skin rendered too dark, so warm mid-tones are blended 50% back toward the input
(`SKIN_RESTORE`), judged on the input pixel so blues, greens and neutrals are untouched.

**Explicitly not done:**
- Clarity +3 (local contrast) can't be expressed in a per-pixel color LUT and is omitted.
- Not tuned against Pixel HDR+ output (unlike ADR 0005/0007): the reference photos were non-Pixel JPEGs. Constants may need
  a Pixel-specific retune, following the same by-eye loop, if the on-device result looks off.
- The indoor/low-light case renders dark; accepted, since the user wouldn't choose this look for such shots.
- Still bound by ADR 0003's CC-BY-NC-SA constraint; the derived LUT is a derivative of the abpy pack.
