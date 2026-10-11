package com.fujivibe.render.tools

import com.fujivibe.render.Rgb

/**
 * Pointwise Charcoal correction: white balance, then a yellow-filter monochrome conversion, then a
 * hard S-curve. Each stage mirrors a setting from Kevin Mullins' black-and-white recipe (Acros + Ye;
 * WB Shift R-4/B-3; Highlight +4; Shadow +4) — see `.scratch/charcoal/spec.md`. The recipe's
 * Sharpness +2 is a spatial filter and Grain Strong/Small is handled by `GrainRenderPipeline`, so
 * neither lives here. Every constant is a starting guess — re-run `BakeCharcoalLut` after editing one.
 */
object CharcoalCorrection {

    /** WB shift R-4 / B-3: a slight pull on red and blue before conversion, as per-channel gains. */
    const val WB_RED_GAIN = 0.97f
    const val WB_BLUE_GAIN = 0.97f

    /**
     * Acros + Ye: panchromatic film behind a yellow filter. The filter blocks blue, so blue (sky,
     * water, denim) renders darker, while red and green (skin, foliage, sand) stay bright. Blue is
     * weighted slightly negative because pale sky is bright in every channel, so merely zeroing blue
     * barely darkens it. Sums to 1 so neutral grays map to themselves.
     */
    const val MONO_RED_WEIGHT = 0.50f
    const val MONO_GREEN_WEIGHT = 0.54f
    const val MONO_BLUE_WEIGHT = -0.04f

    /**
     * Shadow +4 / Highlight +4: the S-curve `x^p / (x^p + (1-x)^p)`, fixed at both endpoints and
     * symmetric about 0.5. `p > 1` pushes tones below 0.5 down (deeper blacks) and tones above 0.5
     * up (brighter whites). Shadow-side and highlight-side steepness are separate knobs.
     */
    const val SHADOW_CURVE_STEEPNESS = 1.7f
    const val HIGHLIGHT_CURVE_STEEPNESS = 1.7f

    fun whiteBalance(rgb: Rgb): Rgb = Rgb(
        (rgb.r * WB_RED_GAIN).coerceIn(0f, 1f),
        rgb.g,
        (rgb.b * WB_BLUE_GAIN).coerceIn(0f, 1f),
    )

    fun mono(rgb: Rgb): Float =
        (MONO_RED_WEIGHT * rgb.r + MONO_GREEN_WEIGHT * rgb.g + MONO_BLUE_WEIGHT * rgb.b).coerceIn(0f, 1f)

    /** Monotonic, `0 -> 0` and `1 -> 1`. */
    fun toneCurve(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        val steepness = if (x < 0.5f) SHADOW_CURVE_STEEPNESS else HIGHLIGHT_CURVE_STEEPNESS
        val xp = Math.pow(x.toDouble(), steepness.toDouble())
        val oneMinusXp = Math.pow(1.0 - x, steepness.toDouble())
        return (xp / (xp + oneMinusXp)).toFloat()
    }

    /** Always returns a neutral gray (`r == g == b`). */
    fun correct(rgb: Rgb): Rgb {
        val value = toneCurve(mono(whiteBalance(rgb)))
        return Rgb(value, value, value)
    }
}
