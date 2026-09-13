---
status: accepted
---

# Remove the original, unmodified Nostalgic Neg. — Nostalgic Neg. (Pixel) is the only Nostalgic Neg. option

ADR 0007 shipped Nostalgic Neg. (Pixel) as a second, separate option alongside the untouched
original Nostalgic Neg., specifically so both could be compared side by side while the Pixel
correction was tuned. That tuning is done: after two rounds against real Pixel photos with the
`PreviewFilmSimulation` tool (see
`.scratch/nostalgic-neg-pixel-lut/issues/01-bake-nostalgic-neg-pixel-lut.md` for the tuning
history — the first round read as a blanket global white-balance shift and was retuned to confine
warmth to shadows/lower-midtones, keep the white point clean, and moderate the green/blue
desaturation), the user confirmed Nostalgic Neg. (Pixel) matches what they want, and no longer has
a use for the plain original — a Pixel JPEG run through the unmodified Provia→Nostalgic Neg
conversion isn't a look they want to keep choosing between. Same sequencing as ADR 0006's Classic
Neg. removal.

**Decision:** remove `FilmSimulation.NOSTALGIC_NEG` from the registry entirely. `ReviewCycle`
automatically drops it from the swipe cycle, since it derives its order directly from
`FilmSimulation.entries`. The registry is now exactly two entries, `CLASSIC_NEG_PIXEL` and
`NOSTALGIC_NEG_PIXEL` — both FujiVibe-derived Pixel-compensated variants, none a direct
pass-through of a stock conversion LUT.

This does **not** remove `raw-assets/.../provia conversion luts/Provia to Nostalgic Neg sRGB.cube`
from disk or from the build: `BakeNostalgicNegPixelLut` still reads that file directly (by path,
not through the `FilmSimulation` registry) as the source it derives Nostalgic Neg. (Pixel) from.
Only the registry entry — the user-facing, selectable option — is gone. `render/build.gradle.kts`'s
test-resources `include(...)` list no longer lists the original file, since no test loads it via
classpath resource once nothing in the registry points at it.

This supersedes ADR 0007's framing of Nostalgic Neg. (Pixel) as shipping "alongside the untouched
`NOSTALGIC_NEG`" — that was true at the time, for exactly the comparison purpose described above,
but was never meant to be permanent.

**Naming left as-is:** the surviving entry keeps its "(Pixel)" qualifier (`"Nostalgic Neg.
(Pixel)"`) rather than being renamed to plain `"Nostalgic Neg."`, same open question ADR 0006 left
for Classic Neg. (Pixel) — revisit both together if the qualifier reads as confusing now that
neither has a second option to disambiguate against.
