---
status: accepted
---

# Remove the original, unmodified Classic Neg. — Classic Neg. (Pixel) is the only Classic Neg. option

ADR 0005 shipped Classic Neg. (Pixel) as a second, separate option alongside the untouched
original Classic Neg., specifically so both could be compared side by side while the Pixel
correction was tuned. That tuning is done: after iterating against a reference image with the
`PreviewFilmSimulation` tool (see `.scratch/classic-neg-pixel-lut/issues/01-bake-classic-neg-pixel-lut.md`
for the tuning history), the user confirmed Classic Neg. (Pixel) matches what they want, and no
longer has a use for the plain original — a Pixel JPEG run through the unmodified Provia→Classic
Neg conversion isn't a look they want to keep choosing between.

**Decision:** remove `FilmSimulation.CLASSIC_NEG` from the registry entirely. `ReviewCycle`
automatically drops it from the swipe cycle, since it derives its order directly from
`FilmSimulation.entries`. The registry is now exactly two entries: `CLASSIC_NEG_PIXEL` and
`NOSTALGIC_NEG`.

This does **not** remove `raw-assets/.../provia conversion luts/Provia to Classic Neg sRGB.cube`
from disk or from the build: `BakeClassicNegPixelLut` still reads that file directly (by path, not
through the `FilmSimulation` registry) as the source it derives Classic Neg. (Pixel) from. Only the
registry entry — the user-facing, selectable option — is gone.

This supersedes ADR 0005's framing of Classic Neg. (Pixel) as shipping "alongside the untouched
original Classic Neg." — that was true at the time, for exactly the comparison purpose described
above, but was never meant to be permanent.

**Naming left as-is:** the surviving entry keeps its "(Pixel)" qualifier (`"Classic Neg. (Pixel)"`)
rather than being renamed to plain `"Classic Neg."`, even though there's no longer a second option
to disambiguate against. Revisit if the qualifier reads as confusing now that it's the only one.
