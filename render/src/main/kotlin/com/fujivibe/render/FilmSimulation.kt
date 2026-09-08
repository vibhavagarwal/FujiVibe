package com.fujivibe.render

/**
 * The fixed, ordered v1 launch set (per ADR 0004): exactly Classic Neg. and Nostalgic Neg.,
 * each wired to its `provia conversion luts/` sRGB `.cube` file — not the same-named files
 * under `cube lut/`, which require scene-linear input and aren't safe to apply directly to
 * a gamma-encoded JPEG.
 */
enum class FilmSimulation(val displayName: String, val cubeResourceName: String) {
    CLASSIC_NEG("Classic Neg.", "Provia to Classic Neg sRGB.cube"),
    NOSTALGIC_NEG("Nostalgic Neg.", "Provia to Nostalgic Neg sRGB.cube"),
}
