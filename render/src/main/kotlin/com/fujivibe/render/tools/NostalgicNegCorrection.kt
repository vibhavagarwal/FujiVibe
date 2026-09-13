package com.fujivibe.render.tools

import com.fujivibe.render.Rgb

/**
 * Pointwise Nostalgic Neg. (Pixel) correction: tone curve, then split-tone, then HSL shift.
 * Deliberately independent of [LutCorrection] (own constants, own copy of the HSL math) — the
 * target look here is a warm push confined to shadows/lower-midtones (not a global white-balance
 * shift), compressed highlights, and cool-hue desaturation with reds/oranges protected. See
 * `.scratch/nostalgic-neg-pixel-lut/spec.md`. Every constant here is a starting guess — re-run
 * `BakeNostalgicNegPixelLut` after editing one.
 *
 * Retuned after a first round of user feedback against real photos: the initial pass applied
 * warmth uniformly across the whole tonal range (including highlights), which read as "a blanket
 * amber filter" rather than a 1970s-style graded look — skin tones went excessively orange,
 * foliage turned to "yellow mud," and white paper/plastic washed out to sepia. The fix: confine
 * warmth to shadows/lower-midtones ([SPLIT_TONE_SHADOW_REACH] dropped so it fades out well before
 * the white point), zero out the highlight tint entirely, moderate the green/blue desaturation
 * so it reads as "slightly muted" rather than muddying, and boost red/orange retention so reds
 * stay deliberately rich against the (now more modestly) muted background.
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
     * Neutral highlight offset — retuned to zero after user feedback against real photos: the
     * first pass's warm highlight tint (+10/+6/-10) read as the whole white point being "washed
     * in sepia" (paper, clear plastic) rather than a subtle vintage cast. Real 1970s-style warm
     * casts sit in the shadows/lower-midtones; the white point should stay reasonably clean.
     */
    val HIGHLIGHT_TINT = Rgb(0f, 0f, 0f)

    /**
     * How far [SHADOW_TINT]'s warm cast reaches up the tonal scale before fading to nothing:
     * shadow weight is `1 - luma^SPLIT_TONE_SHADOW_REACH`. Dropped from 2f to 0.6f after user
     * feedback: at 2f, shadow weight was still ~0.75 at 50%-gray luma, pushing skin tones
     * (roughly midtone) and midtone foliage into an "excessively orange" / "yellow mud" cast —
     * a global-white-balance-shift symptom rather than the intended "amber shadows and lower
     * midtones only" look. At 0.6f, shadow weight drops to ~0.34 by 50% gray and ~0.06 by 90%
     * luma (near-white paper), concentrating warmth in true shadows/lower-midtones and leaving
     * highlights and the white point clean (reinforced by [HIGHLIGHT_TINT] now being neutral).
     */
    const val SPLIT_TONE_SHADOW_REACH = 0.6f

    /**
     * Blends [SHADOW_TINT] into shadows/lower-midtones, fading to [HIGHLIGHT_TINT] (neutral, a
     * no-op) by the highlights — so only the low end of the tonal range picks up warmth. Clamped
     * to `[0, 1]`: an unclamped >1 channel here would otherwise reach [rgbToHsl] out of its
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

    /**
     * Greens desaturated — the brief calls for "pulling vibrancy out of blues and greens."
     * Reduction cut from 0.45 to 0.28 after user feedback that combined with the (now-fixed)
     * over-reaching shadow tint, foliage was reading as "uniform yellow mud" rather than
     * "slightly muted" — greens should stay recognizably green, just less vivid.
     */
    const val GREENS_TARGET_HUE = 120f
    const val GREENS_WIDTH = 45f
    const val GREENS_SAT_REDUCTION = 0.28f

    /** Blues/cyans desaturated, same reasoning and same reduction cut as greens. */
    const val BLUES_TARGET_HUE = 225f
    const val BLUES_WIDTH = 55f
    const val BLUES_SAT_REDUCTION = 0.28f

    /**
     * Reds/oranges keep full saturation or gain a little — "elements like the red logo
     * maintain a rich, warm focus against the faded background." Boost raised from 0.10 to
     * 0.18 after user feedback asking reds to "retain their deep saturation" more assertively,
     * so they read as deliberately rich against the now-more-modestly-muted background rather
     * than blending into a uniformly warm cast.
     */
    const val REDS_ORANGES_TARGET_HUE = 15f
    const val REDS_ORANGES_WIDTH = 35f
    const val REDS_ORANGES_SAT_BOOST = 0.18f

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
