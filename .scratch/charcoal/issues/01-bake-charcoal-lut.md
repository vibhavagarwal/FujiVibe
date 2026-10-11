# 01: Bake and ship the "Charcoal" (black-and-white recipe) derived LUT

**Status:** done (render and unit tests pass; on-device verification pending). Tuned against 5 user photos over two
rounds: first pass read right but skies were a flat mid-grey -> blue weight made slightly negative (+0.03 -> -0.04) and
red weight raised; grain read as soft mottling at 100% -> 700 to 900 cells across the width.

- [x] `CharcoalCorrection` + `BakeCharcoalLut` with unit tests
- [x] `derived-luts/Charcoal sRGB.cube` committed (32-point, like its siblings)
- [x] `FilmSimulation.CHARCOAL` last in the registry; per-look grain profile; preview tool wired
- [x] `ReviewCycleTest` updated for the 5-entry swipe order
- [x] ADR 0011 written
- [x] Visually verified on 5 real photos via the preview tool
- [ ] User sign-off on the look (before/after shared)
- [ ] On-device verification (adb install, real phone)
