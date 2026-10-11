package com.fujivibe.render

/**
 * The ordered Film Simulation registry, each wired to a `.cube` resource on the classpath.
 * Classic Neg. (Pixel) and Nostalgic Neg. (Pixel) are FujiVibe's own derived variants, not stock
 * Fuji-named looks: each compensates for how a Pixel phone's JPEG differs from the Provia
 * baseline the original conversion LUTs assume (see ADR 0005/0007), superseding ADR 0004's
 * "exactly two" framing. The original, unmodified Classic Neg. and Nostalgic Neg. entries were
 * each removed once their Pixel-compensated variant replaced them as the only version users
 * actually want (see ADR 0006 and ADR 0008). Koda64 (Pixel), the Kodachrome 64 recipe, is likewise FujiVibe-derived: the
 * pack's linear-input Classic Chrome LUT with the sRGB->linear step baked in, plus a correction
 * approximating the fujixweekly recipe (see ADR 0010) — so every entry here is a FujiVibe-derived
 * variant, none a direct pass-through of a stock conversion LUT. Charcoal is the one look with no
 * source LUT at all: a black-and-white recipe (Acros + Yellow filter) baked straight from a
 * monochrome conversion and tone curve (see ADR 0011). [shortName] is the acronym shown where there's
 * no room for the full name (e.g. over a Library thumbnail); each look needs its own.
 */
enum class FilmSimulation(val displayName: String, val shortName: String, val cubeResourceName: String) {
    CLASSIC_NEG_PIXEL("Classic Neg. (Pixel)", "CN", "Classic Neg Pixel sRGB.cube"),
    NOSTALGIC_NEG_PIXEL("Nostalgic Neg. (Pixel)", "NN", "Nostalgic Neg Pixel sRGB.cube"),
    KODA64_PIXEL("Koda64 (Pixel)", "K", "Koda64 Pixel sRGB.cube"),
    CHARCOAL("Charcoal", "CH", "Charcoal sRGB.cube"),
}
