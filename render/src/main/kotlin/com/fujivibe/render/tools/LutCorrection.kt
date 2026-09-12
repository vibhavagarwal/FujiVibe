package com.fujivibe.render.tools

import com.fujivibe.render.Rgb

/**
 * Pointwise Classic Neg. (Pixel) correction: tone curve, then split-tone, then HSL shift.
 * Every constant here is a tunable knob (see `.scratch/classic-neg-pixel-lut/spec.md`) —
 * re-run `BakeClassicNegPixelLut` after editing one.
 *
 * Retuned against a reference image the user supplied: a uniformly cool/teal, heavily
 * desaturated, matte-lifted-black look, with no warm push anywhere — a different direction
 * than the original text spec's "warm highlights," which visibly overshot into pink/orange
 * on near-neutral tones (paper, walls) when tried against a real photo.
 */
object LutCorrection {

    /** Black point lifted ~4%, for a matte, faded-black look. */
    const val BLACK_POINT_LIFT = 0.04f

    /**
     * Shadow-side steepness of `x^p / (x^p + (1-x)^p)` (`x < 0.5`), symmetric about 0.5, fixed
     * at both endpoints. `p > 1` compresses shadows below the input value. Lowered from the
     * original 2.2 (applying this per-channel, not on luma, adds saturation wherever a pixel's
     * channels differ, which was fighting the reference's muted look) but raised back up from
     * an intermediate 1.6, which overcorrected into reading noticeably too bright/flat.
     */
    const val SHADOW_CURVE_STEEPNESS = 1.9f

    /**
     * Highlight-side steepness (`x >= 0.5`). `p = 1` is the identity (no boost, no cut); `p < 1`
     * pulls values above 0.5 back down toward it. A shared single steepness (the original
     * design) pushed highlights above the input value exactly as much as it pulled shadows
     * below it, which read as true to the shadows but visibly over-brightened near-white areas
     * (e.g. a paper towel) well past the original. `p = 1` alone fixed most of that; 0.9 trimmed
     * more but still read too bright per user feedback against a real photo; dropped further.
     */
    const val HIGHLIGHT_CURVE_STEEPNESS = 0.8f

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

    /** Cool cyan-green shadow offset, 8-bit +0/+6/+4 normalized to [0, 1]. */
    val SHADOW_TINT = Rgb(0f / 255f, 6f / 255f, 4f / 255f)

    /**
     * Cool highlight offset (retuned from the original warm amber/cream): less red, more blue,
     * 8-bit -3/0/+3 normalized to [0, 1] — the reference's highlights stay cool/muted, not warm.
     */
    val HIGHLIGHT_TINT = Rgb(-3f / 255f, 0f / 255f, 3f / 255f)

    /**
     * How far [SHADOW_TINT]'s cool cast reaches up the tonal scale before fading: shadow weight
     * is `1 - luma^SHADOW_REACH`, which (for `SHADOW_REACH > 1`) stays close to full strength
     * through midtones and only drops off near peak highlights, rather than fading linearly
     * from the very start. Reference image called for the cool cast to extend into midtones,
     * leaving only peak highlights near-neutral with a faint cool tint.
     */
    const val SPLIT_TONE_SHADOW_REACH = 3f

    /**
     * Blends [SHADOW_TINT] into shadows/midtones and [HIGHLIGHT_TINT] into peak highlights, by
     * [SPLIT_TONE_SHADOW_REACH]. Clamped to `[0, 1]`: an unclamped >1 channel here would
     * otherwise reach [rgbToHsl] out of its documented domain, driving saturation negative and
     * collapsing the result to flat white once `hslShift` coerces it back — silently erasing the
     * tint it was meant to add.
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
     * Reds rotated toward magenta/brick. Target hue, influence width, and shift amount.
     * Saturation boost cut way down from the original 0.08 — boosting red saturation fights the
     * reference's overall muted look; kept slightly positive rather than 0 so a red push still
     * reads as intentional, not accidental.
     */
    const val REDS_TARGET_HUE = 0f
    const val REDS_WIDTH = 40f
    const val REDS_HUE_SHIFT_DEG = -10f
    const val REDS_SAT_BOOST = 0.02f
    const val REDS_LUM_DROP = 0.05f

    /** Blues/cyans rotated toward dark teal — the reference's countertop reads richly teal. */
    const val BLUES_TARGET_HUE = 240f
    const val BLUES_WIDTH = 45f
    const val BLUES_HUE_SHIFT_DEG = -25f

    /** Extra saturation cut for yellows/oranges specifically, on top of the global cut. */
    const val YELLOW_ORANGE_TARGET_HUE = 45f
    const val YELLOW_ORANGE_WIDTH = 35f
    const val YELLOW_ORANGE_SAT_REDUCTION = 0.15f

    /**
     * Global vibrance reduction, raised substantially from the original 10-15% (0.12): the
     * reference reads as heavily desaturated/muted throughout, well beyond a mild pull-down.
     */
    const val GLOBAL_VIBRANCE_REDUCTION = 0.4f

    /** `1` at `target`, falling linearly to `0` at `width` degrees away (shortest way around). */
    private fun angularWeight(hueDegrees: Float, target: Float, width: Float): Float {
        val rawDiff = Math.abs(hueDegrees - target) % 360f
        val distance = if (rawDiff > 180f) 360f - rawDiff else rawDiff
        return (1f - distance / width).coerceIn(0f, 1f)
    }

    /** Targeted red/blue hue rotation plus saturation/lightness adjustment, then global vibrance. */
    fun hslShift(rgb: Rgb): Rgb {
        val hsl = rgbToHsl(rgb)

        val redWeight = angularWeight(hsl.hueDegrees, REDS_TARGET_HUE, REDS_WIDTH)
        var hue = (hsl.hueDegrees + REDS_HUE_SHIFT_DEG * redWeight).mod(360f)
        var saturation = hsl.saturation + REDS_SAT_BOOST * redWeight
        val lightness = hsl.lightness - REDS_LUM_DROP * redWeight

        val blueWeight = angularWeight(hsl.hueDegrees, BLUES_TARGET_HUE, BLUES_WIDTH)
        hue = (hue + BLUES_HUE_SHIFT_DEG * blueWeight).mod(360f)

        val yellowOrangeWeight = angularWeight(hsl.hueDegrees, YELLOW_ORANGE_TARGET_HUE, YELLOW_ORANGE_WIDTH)
        saturation *= (1f - YELLOW_ORANGE_SAT_REDUCTION * yellowOrangeWeight)

        saturation *= (1f - GLOBAL_VIBRANCE_REDUCTION)

        return hslToRgb(Hsl(hue, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f)))
    }

    /** The full Classic Neg. (Pixel) correction: tone curve, then split-tone, then HSL shift. */
    fun correct(rgb: Rgb): Rgb {
        val toned = Rgb(toneCurve(rgb.r), toneCurve(rgb.g), toneCurve(rgb.b))
        return hslShift(splitTone(toned))
    }
}
