---
status: accepted
---

# v1 launch set reduced to Classic Neg. and Nostalgic Neg. only

The `abpy/FujifilmCameraProfiles` pack (ADR 0003) states its main film-look LUTs (Provia, Velvia, Astia, Classic Chrome, Pro Neg. Std/Hi, Reala Ace, under `cube lut/`) require scene-linear input, not gamma-encoded sRGB — a phone JPEG is gamma-encoded, so applying these directly (as ADR 0001/0002 commit to: CPU-side, no pre-transform) would likely look visibly wrong, probably worse than the earlier F-Log mismatch.

The pack separately ships `provia conversion luts/`, which its own README states can be applied directly to camera JPEGs: `Provia to Classic Neg sRGB.cube` and `Provia to Nostalgic Neg sRGB.cube` (a third file in that folder, `Provia to Bleach Bypass sRGB.cube`, is not needed — Bleach Bypass was already dropped from scope during requirements grilling).

Rather than build a linear color-space pre-transform to unlock the other 7 simulations, v1 ships **only Classic Neg. and Nostalgic Neg.**, using specifically the files in `provia conversion luts/` (not the same-named files under `cube lut/`, which are the linear-input versions and must not be used directly). This keeps the render pipeline exactly as simple as ADR 0001/0002 already committed to, at the cost of a much smaller launch set.

The other 7 simulations (Provia, Velvia, Astia, Classic Chrome, Pro Neg. Std/Hi, Reala Ace) are deferred, not abandoned. Re-adding any of them later requires either implementing a linear pre-transform, or separately verifying that the direct-apply mismatch looks acceptable in practice despite the pack's own warning.
