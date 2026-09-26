package com.fujivibe.render.tools

import com.fujivibe.render.Rgb

/**
 * Pointwise Kodachrome 64 correction, applied on top of the pack's Classic Chrome LUT output:
 * white balance, then tone curve, then color. Each stage mirrors a setting from the fujixweekly
 * "Kodachrome 64" recipe (Classic Chrome base; WB Daylight +2 Red/-5 Blue; Shadow +0.5; DR200;
 * Color +2; Color Chrome Effect Strong) — see `.scratch/kodachrome-64/spec.md`. The recipe's
 * Clarity +3 is local contrast, which a per-pixel color LUT can't express, so it's omitted.
 * Every constant is a starting guess — re-run `BakeKodachrome64Lut` after editing one.
 */
object KodachromeCorrection {

    /** WB shift +2 Red / -5 Blue: a modest warm push, as per-channel gains. */
    const val WB_RED_GAIN = 1.03f
    const val WB_BLUE_GAIN = 0.93f

    /** Shadow +0.5: `> 1` deepens lower tones slightly. */
    const val SHADOW_GAMMA = 1.06f

    /** DR200: highlights above [SHOULDER_KNEE] roll off, pulling the white point just under 1. */
    const val SHOULDER_KNEE = 0.75f
    const val SHOULDER_STRENGTH = 0.15f

    /** Color +2: overall saturation boost. */
    const val COLOR_BOOST = 0.12f

    /** Color Chrome Effect Strong: saturated colors get slightly darker/denser. */
    const val COLOR_CHROME_DARKEN = 0.10f

    /**
     * Classic Chrome pushes sky blues toward pale teal; Kodachrome skies are deep, rich blue. Blue-
     * dominant (cyan-to-blue) pixels get their green pulled back toward red by this fraction, which
     * deepens the blue without touching green-leaning teals (foliage, sea-green water).
     */
    const val BLUE_DEEPEN = 0.20f

    fun whiteBalance(rgb: Rgb): Rgb = Rgb(
        (rgb.r * WB_RED_GAIN).coerceIn(0f, 1f),
        rgb.g,
        (rgb.b * WB_BLUE_GAIN).coerceIn(0f, 1f),
    )

    /** Monotonic: shadow gamma, then a soft shoulder above [SHOULDER_KNEE]. */
    fun toneCurve(x: Float): Float {
        val deepened = Math.pow(x.coerceIn(0f, 1f).toDouble(), SHADOW_GAMMA.toDouble()).toFloat()
        if (deepened <= SHOULDER_KNEE) return deepened
        val t = (deepened - SHOULDER_KNEE) / (1f - SHOULDER_KNEE)
        return SHOULDER_KNEE + (1f - SHOULDER_KNEE) * (t - SHOULDER_STRENGTH * t * t)
    }

    fun color(rgb: Rgb): Rgb {
        val luma = 0.2126f * rgb.r + 0.7152f * rgb.g + 0.0722f * rgb.b
        val scale = 1f + COLOR_BOOST
        val r = luma + (rgb.r - luma) * scale
        val gBoosted = luma + (rgb.g - luma) * scale
        val b = luma + (rgb.b - luma) * scale

        val g = gBoosted - BLUE_DEEPEN * blueDominance(r, gBoosted, b) * maxOf(gBoosted - r, 0f)

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val saturation = if (max <= 0f) 0f else ((max - min) / max).coerceIn(0f, 1f)
        val density = 1f - COLOR_CHROME_DARKEN * saturation

        return Rgb(
            (r * density).coerceIn(0f, 1f),
            (g * density).coerceIn(0f, 1f),
            (b * density).coerceIn(0f, 1f),
        )
    }

    /** 1 for blue-leaning pixels (clearly bluer than red, b >= g), fading out as green overtakes blue. */
    private fun blueDominance(r: Float, g: Float, b: Float): Float {
        val overRed = ((b - r) / 0.25f).coerceIn(0f, 1f)
        val overGreen = (1f - (g - b) / 0.08f).coerceIn(0f, 1f)
        return overRed * overGreen
    }

    fun correct(rgb: Rgb): Rgb {
        val balanced = whiteBalance(rgb)
        val toned = Rgb(toneCurve(balanced.r), toneCurve(balanced.g), toneCurve(balanced.b))
        return color(toned)
    }
}
