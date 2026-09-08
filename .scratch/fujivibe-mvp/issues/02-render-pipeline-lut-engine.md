# 02: Render pipeline: Classic Neg. & Nostalgic Neg. LUT engine

**What to build:** A render pipeline with a single interface taking a source bitmap and a Film Simulation-or-Original, returning a rendered bitmap. It parses `.cube` text files into an in-memory 3D lookup table and applies them via CPU-side trilinear interpolation (no GPU/OpenGL, no color-space pre-transform) — the same logic serves both a downscaled preview and a full-resolution export without separate code paths. The Film Simulation registry is a fixed, ordered list of exactly two entries: Classic Neg. and Nostalgic Neg. Their `.cube` files are wired in from the `abpy/FujifilmCameraProfiles` pack's `provia conversion luts/` folder specifically (`Provia to Classic Neg sRGB.cube`, `Provia to Nostalgic Neg sRGB.cube`) — not the same-named files under that pack's `cube lut/` folder, which require scene-linear input and are not safe to apply directly to a gamma-encoded JPEG (see ADR 0004). Original is not a registry entry — selecting it means no LUT is loaded or applied at all.

**Blocked by:** None (pure JVM module — no UI or camera dependency, can proceed in parallel with ticket 01)

**Status:** ready-for-agent

- [ ] `.cube` parser correctly loads a 3D LUT from the pack's text format
- [ ] The two launch Film Simulations are wired to the correct `provia conversion luts/` sRGB files (not the `cube lut/` files of the same name)
- [ ] Trilinear interpolation produces correct output at known sample points for both LUTs
- [ ] Selecting Original applies no LUT and returns the source bitmap byte-for-byte unchanged
- [ ] The same render pipeline call works against both a downscaled bitmap and a full-resolution bitmap
- [ ] LUT application runs on a background coroutine, never the UI thread
- [ ] Fully covered by JVM unit tests requiring no emulator or device, per the spec's Testing Decisions
