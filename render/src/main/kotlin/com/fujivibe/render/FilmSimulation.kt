package com.fujivibe.render

/**
 * The ordered Film Simulation registry, each wired to a `.cube` resource on the classpath.
 * Nostalgic Neg. is wired to its `provia conversion luts/` sRGB `.cube` file — not the
 * same-named file under `cube lut/`, which requires scene-linear input and isn't safe to apply
 * directly to a gamma-encoded JPEG (v1 launch set per ADR 0004). Classic Neg. (Pixel) is
 * FujiVibe's own derived variant, not a stock Fuji-named look: it compensates for how a Pixel
 * phone's JPEG differs from the Provia baseline the conversion LUTs assume (see ADR 0005),
 * superseding ADR 0004's "exactly these two" framing. The original, unmodified Classic Neg. was
 * removed once Classic Neg. (Pixel) replaced it as the only Classic Neg.-family option users
 * actually want (see ADR 0006).
 */
enum class FilmSimulation(val displayName: String, val cubeResourceName: String) {
    CLASSIC_NEG_PIXEL("Classic Neg. (Pixel)", "Classic Neg Pixel sRGB.cube"),
    NOSTALGIC_NEG("Nostalgic Neg.", "Provia to Nostalgic Neg sRGB.cube"),
}
