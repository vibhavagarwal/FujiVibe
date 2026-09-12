package com.fujivibe.render.tools

import com.fujivibe.render.Rgb

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

    /**
     * Reddish-brown shadow offset, 8-bit +12/+2/-8 normalized to [0, 1] — the "milky, faded
     * blacks with a very subtle warm or reddish-brown undertone" the brief calls for.
     */
    val SHADOW_TINT = Rgb(12f / 255f, 2f / 255f, -8f / 255f)

    /**
     * Warm cream/amber highlight offset, 8-bit +10/+6/-10 — unlike Classic Neg. (Pixel), this
     * does NOT fade toward neutral/cool at the highlight end: the brief wants "creamy, warm,
     * slightly vintage" whites, not stark digital white.
     */
    val HIGHLIGHT_TINT = Rgb(10f / 255f, 6f / 255f, -10f / 255f)

    /**
     * How far [SHADOW_TINT]'s warm cast reaches up the tonal scale before [HIGHLIGHT_TINT]
     * takes over: shadow weight is `1 - luma^SPLIT_TONE_SHADOW_REACH`. Lower than Classic Neg.
     * (Pixel)'s 3f since both tints are warm here — there's no need for the shadow tint to
     * dominate all the way into highlights the way a cool-vs-neutral design would require.
     */
    const val SPLIT_TONE_SHADOW_REACH = 2f

    /**
     * Blends [SHADOW_TINT] into shadows and [HIGHLIGHT_TINT] into highlights. Clamped to
     * `[0, 1]`: an unclamped >1 channel here would otherwise reach [rgbToHsl] out of its
     * documented domain (see the equivalent bug fixed in `LutCorrection.splitTone`).
     */
    fun splitTone(rgb: Rgb): Rgb {
        val luma = 0.2126f * rgb.r + 0.7152f * rgb.g + 0.0722f * rgb.b
        val highlightWeight = Math.pow(luma.toDouble(), SPLIT_TONE_SHADOW_REACH.toDouble()).toFloat()
        val shadowWeight = 1f - highlightWeight
        return Rgb(
            (rgb.r + SHADOW_TINT.r * shadowWeight + HIGHLIGHT_TINT.r * highlightWeight).coerceIn(0f, 1f),
            (rgb.g + SHADOW_TINT.g * shadowWeight + HIGHLIGHT_TINT.g * highlightWeight).coerceIn(0f, 1f),
            (rgb.b + SHADOW_TINT.b * shadowWeight + HIGHLIGHT_TINT.b * highlightWeight).coerceIn(0f, 1f),
        )
    }
}
