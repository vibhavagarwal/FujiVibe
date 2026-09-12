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

    /** Hue in `[0, 360)` degrees, saturation and lightness in `[0, 1]`. */
    data class Hsl(val hueDegrees: Float, val saturation: Float, val lightness: Float)

    fun rgbToHsl(rgb: Rgb): Hsl {
        val max = maxOf(rgb.r, rgb.g, rgb.b)
        val min = minOf(rgb.r, rgb.g, rgb.b)
        val delta = max - min
        val lightness = (max + min) / 2f

        if (delta == 0f) return Hsl(0f, 0f, lightness)

        val saturation = delta / (1f - Math.abs(2f * lightness - 1f))

        val hue = when (max) {
            rgb.r -> 60f * (((rgb.g - rgb.b) / delta).mod(6f))
            rgb.g -> 60f * (((rgb.b - rgb.r) / delta) + 2f)
            else -> 60f * (((rgb.r - rgb.g) / delta) + 4f)
        }

        return Hsl(hue, saturation, lightness)
    }

    fun hslToRgb(hsl: Hsl): Rgb {
        val c = (1f - Math.abs(2f * hsl.lightness - 1f)) * hsl.saturation
        val hPrime = hsl.hueDegrees / 60f
        val x = c * (1f - Math.abs(hPrime.mod(2f) - 1f))
        val m = hsl.lightness - c / 2f

        val (r1, g1, b1) = when {
            hPrime < 1f -> Triple(c, x, 0f)
            hPrime < 2f -> Triple(x, c, 0f)
            hPrime < 3f -> Triple(0f, c, x)
            hPrime < 4f -> Triple(0f, x, c)
            hPrime < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return Rgb(r1 + m, g1 + m, b1 + m)
    }

    /** Greens desaturated — the brief calls for "pulling vibrancy out of blues and greens." */
    const val GREENS_TARGET_HUE = 120f
    const val GREENS_WIDTH = 45f
    const val GREENS_SAT_REDUCTION = 0.45f

    /** Blues/cyans desaturated, same reasoning as greens. */
    const val BLUES_TARGET_HUE = 225f
    const val BLUES_WIDTH = 55f
    const val BLUES_SAT_REDUCTION = 0.45f

    /**
     * Reds/oranges keep full saturation or gain a little — "elements like the red logo
     * maintain a rich, warm focus against the faded background."
     */
    const val REDS_ORANGES_TARGET_HUE = 15f
    const val REDS_ORANGES_WIDTH = 35f
    const val REDS_ORANGES_SAT_BOOST = 0.10f

    /** `1` at `target`, falling linearly to `0` at `width` degrees away (shortest way around). */
    private fun angularWeight(hueDegrees: Float, target: Float, width: Float): Float {
        val rawDiff = Math.abs(hueDegrees - target) % 360f
        val distance = if (rawDiff > 180f) 360f - rawDiff else rawDiff
        return (1f - distance / width).coerceIn(0f, 1f)
    }

    /**
     * Selective cool-hue desaturation (blues, greens) plus red/orange saturation retention —
     * no blanket global-vibrance cut, unlike `LutCorrection`: desaturation here is targeted at
     * specific hue bands, not applied everywhere.
     */
    fun hslShift(rgb: Rgb): Rgb {
        val hsl = rgbToHsl(rgb)

        val greenWeight = angularWeight(hsl.hueDegrees, GREENS_TARGET_HUE, GREENS_WIDTH)
        val blueWeight = angularWeight(hsl.hueDegrees, BLUES_TARGET_HUE, BLUES_WIDTH)
        val redOrangeWeight = angularWeight(hsl.hueDegrees, REDS_ORANGES_TARGET_HUE, REDS_ORANGES_WIDTH)

        var saturation = hsl.saturation
        saturation *= (1f - GREENS_SAT_REDUCTION * greenWeight)
        saturation *= (1f - BLUES_SAT_REDUCTION * blueWeight)
        saturation *= (1f + REDS_ORANGES_SAT_BOOST * redOrangeWeight)

        return hslToRgb(Hsl(hsl.hueDegrees, saturation.coerceIn(0f, 1f), hsl.lightness))
    }

    /** The full Nostalgic Neg. (Pixel) correction: tone curve, then split-tone, then HSL shift. */
    fun correct(rgb: Rgb): Rgb {
        val toned = Rgb(toneCurve(rgb.r), toneCurve(rgb.g), toneCurve(rgb.b))
        return hslShift(splitTone(toned))
    }
}
