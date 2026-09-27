# 01: Bake and ship the "Koda64 (Pixel)" (Kodachrome 64 recipe) derived LUT

**Status:** done (simulator-verified; on-device verification pending). Tuned against 5 user photos over several rounds:
first pass far too dark/teal (double tone curve) -> tone curve neutralized; blue deepening 0.40 overshot to lavender ->
0.20; skin too dark -> 50% restored toward original. User approved the look ("much better, lock this in").

- [x] `Koda64PixelCorrection` + `BakeKoda64PixelLut` with unit tests
- [x] `derived-luts/Koda64 Pixel sRGB.cube` committed
- [x] `FilmSimulation.KODA64_PIXEL` last in the registry; grain enabled; preview tool wired
- [x] `ReviewCycleTest` updated for the 4-entry swipe order
- [x] ADR 0010 written
- [x] Visually verified on 5 real photos via the preview tool
- [ ] On-device verification (adb install, real phone)
