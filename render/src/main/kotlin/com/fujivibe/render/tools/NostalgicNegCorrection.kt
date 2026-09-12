package com.fujivibe.render.tools

/**
 * Pointwise Nostalgic Neg. (Pixel) correction: tone curve, then split-tone, then HSL shift.
 * Deliberately independent of [LutCorrection] (own constants, own copy of the HSL math) — the
 * target look here is a warm push (amber white balance, warm-tinted lifted blacks, compressed
 * highlights, cool-hue desaturation with reds/oranges protected), essentially inverted from
 * Classic Neg. (Pixel)'s cool/muted direction, so independent tuning knobs matter the same way
 * they did for that ticket. See `.scratch/nostalgic-neg-pixel-lut/spec.md`. Every constant here
 * is a starting guess — re-run `BakeNostalgicNegPixelLut` after editing one.
 */
object NostalgicNegCorrection {

    /**
     * Black point lifted for a milky, faded-black look — higher than Classic Neg. (Pixel)'s
     * 0.04, since the reference brief calls for a more pronounced lift ("shadows should not
     * reach true black").
     */
    const val BLACK_POINT_LIFT = 0.08f

    /** Shadow-side S-curve steepness — mild, since the target look is soft/faded, not punchy. */
    const val SHADOW_CURVE_STEEPNESS = 1.3f

    /**
     * Highlight-side steepness, `< 1` so it compresses (pulls down) rather than boosts — the
     * brief calls for the white point to be "slightly lowered," preventing stark digital whites.
     */
    const val HIGHLIGHT_CURVE_STEEPNESS = 0.75f

    /** Tone curve applied identically to each channel: S-curve contrast, then black-point lift. */
    fun toneCurve(x: Float): Float {
        val steepness = if (x < 0.5f) SHADOW_CURVE_STEEPNESS else HIGHLIGHT_CURVE_STEEPNESS
        val curved = sCurve(x, steepness)
        return BLACK_POINT_LIFT + (1f - BLACK_POINT_LIFT) * curved
    }

    private fun sCurve(x: Float, steepness: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        val xp = Math.pow(x.toDouble(), steepness.toDouble())
        val oneMinusXp = Math.pow((1.0 - x), steepness.toDouble())
        return (xp / (xp + oneMinusXp)).toFloat()
    }
}
