---
status: accepted
---

# Charcoal, the black-and-white look: baked from a monochrome conversion and tone curve, no source LUT

Kevin Mullins' black-and-white recipe (Acros + Yellow filter; Grain Strong/Small; WB Shift R-4/B-3; Highlight +4;
Shadow +4; Sharpness +2; Clarity 0) is a gritty, high-contrast 1970s/80s press-photo look. ADR 0003 left Acros as a
stretch goal with no identified LUT source, and a source LUT isn't needed here: a black-and-white look is a
luminance conversion plus a tone curve, both of which are pointwise.

**Decision:** ship `CHARCOAL` ("Charcoal", the user's name for the recipe look) as a FujiVibe Film Simulation, keeping
the runtime pipeline unchanged (`Cube3DLut`, `LutRenderPipeline`, `ResourceLutLoader` untouched). The new
`derived-luts/Charcoal sRGB.cube` is baked offline by `BakeCharcoalLut` straight from `CharcoalCorrection`, with no
source `.cube`. The grid is the identity cube in sRGB-encoded coordinates, since the app feeds the LUT gamma-encoded
JPEG pixels, so no linearize step (contrast ADR 0010). `CharcoalCorrection` approximates the recipe:

1. WB Shift R-4/B-3 becomes small red and blue gains (0.97) applied before conversion;
2. Acros + Ye becomes a weighted channel sum, R 0.50 / G 0.54 / B -0.04 (sums to 1, so neutrals keep their level).
   Blue is weighted slightly negative rather than zero: pale sky is bright in every channel, so zeroing blue barely
   darkens it, and a first pass at +0.03 left skies a flat mid-grey. The result is deep skies and water, bright skin,
   foliage and sand;
3. Highlight +4 / Shadow +4 becomes a symmetric S-curve `x^p / (x^p + (1-x)^p)`, p = 1.7 on both sides (separate
   knobs, so they can be tuned apart): deeper blacks and brighter whites.

Grain Strong/Small is the one part not in the LUT. `GrainRenderPipeline` previously applied one shared grain to every
look; it now holds a per-look `GrainProfile` (cells across the width, blend opacity). Charcoal's is 900 cells at 18%
opacity, versus the default 40 cells at 5%: grains a few pixels across at full resolution instead of soft clumps, at
several times the strength. The other three looks keep the default profile, so their output is unchanged.

Tuned by eye against five real photos (palm and sea, a portrait against dark lava rock, a portrait against deep blue
sky, a bright cloud over mountains, and a warm-lit indoor shot).

**Explicitly not done:**
- Sharpness +2 is a spatial filter that can't be expressed in a per-pixel color LUT, and Clarity was omitted in ADR 0010
  for the same reason. Phone JPEGs are usually already sharpened.
- Not tuned against Pixel HDR+ output: the reference photos were non-Pixel JPEGs. The contrast constants may need a
  Pixel-specific retune, following the same by-eye loop, if the on-device result looks off.
- Grain is sized relative to image width (as for every look), so on a phone-width Review preview 900 cells is about one
  pixel each. The 100%-crop texture is only seen at full-resolution Export.
- Unlike the other derived LUTs, `Charcoal sRGB.cube` is not an adaptation of the abpy pack, so ADR 0003's
  CC-BY-NC-SA constraint does not apply to it (see `derived-luts/LICENSE.md`).
