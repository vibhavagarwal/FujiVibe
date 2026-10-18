package com.fujivibe.recipe

import com.fujivibe.camera.RawConvProfile
import kotlin.math.roundToInt

/**
 * Writes a [Recipe] into the camera's recipe block, leaving untouched whatever the recipe does not
 * set. Encodings follow FilmKit's notes (MIT); only the film simulation is proven on an X-T5 so
 * far, the rest wait on the field test (.scratch/raw-develop/issues/02).
 */
fun Recipe.applyTo(profile: RawConvProfile): RawConvProfile {
    var p = profile.withFilmSimulation(baseSimulation.code)
    val s = settings

    // Auto has no value of its own that is known; the camera keeps the RAF's.
    s.dynamicRange?.percent()?.let { p = p.with(RawConvProfile.DYNAMIC_RANGE, minOf(it, profile.field(RawConvProfile.DYNAMIC_RANGE))) }
    s.dRangePriority?.let { p = p.with(RawConvProfile.D_RANGE_PRIORITY, it.ordinal) }
    s.grain?.let { p = p.with(RawConvProfile.GRAIN, it.nativeValue()) }
    s.colorChrome?.let { p = p.with(RawConvProfile.COLOR_CHROME, it.nativeValue()) }
    s.colorChromeFxBlue?.let { p = p.with(RawConvProfile.COLOR_CHROME_FX_BLUE, it.nativeValue()) }
    s.whiteBalance?.let { p = it.applyTo(p) }
    s.highlight?.let { p = p.with(RawConvProfile.HIGHLIGHT_TONE, it.times10()) }
    s.shadow?.let { p = p.with(RawConvProfile.SHADOW_TONE, it.times10()) }
    s.color?.let { p = p.with(RawConvProfile.COLOR, it.times10()) }
    s.sharpness?.let { p = p.with(RawConvProfile.SHARPNESS, it.times10()) }
    s.clarity?.let { p = p.with(RawConvProfile.CLARITY, it.times10()) }
    return p
}

/**
 * Asked for more Dynamic Range than a RAF was shot with, the camera rendered a strong green cast
 * (Pacific Blues' DR400 on a RAF recorded at DR200), so [applyTo] never goes above the RAF's own.
 * Provisional: the cause is not confirmed (.scratch/raw-develop/issues/02). Returns a note naming
 * the limit when this recipe is held back by it, so a screen can say so.
 */
fun Recipe.dynamicRangeLimit(profile: RawConvProfile): String? {
    val asked = settings.dynamicRange?.percent() ?: return null
    val shot = profile.field(RawConvProfile.DYNAMIC_RANGE)
    return if (asked > shot) "Dynamic range DR$asked held to DR$shot, as the RAF was shot" else null
}

private fun DynamicRange.percent(): Int? = when (this) {
    DynamicRange.DR100 -> 100
    DynamicRange.DR200 -> 200
    DynamicRange.DR400 -> 400
    DynamicRange.AUTO -> null
}

/** Settings of this recipe that [applyTo] cannot write yet, by name, so a screen can say so. */
fun Recipe.unsupportedSettings(): List<String> = buildList {
    // Noise reduction uses a lookup table not yet known; black-and-white colour shifts have no known position.
    if (settings.highIsoNr != null) add("High ISO NR")
    if (settings.monochromaticColor != null) add("Monochromatic colour")
    if (settings.dynamicRange == DynamicRange.AUTO) add("Dynamic range auto")
}

private fun Double.times10(): Int = (this * 10).roundToInt()

/** Off, weak, strong are 1, 2, 3 in the camera's block. */
private fun Level.nativeValue(): Int = ordinal + 1

/** Grain is one flat code: off 1, weak small 2, strong small 3, weak large 4, strong large 5. */
private fun Grain.nativeValue(): Int = when {
    strength == GrainStrength.OFF -> 1
    size == GrainSize.LARGE -> if (strength == GrainStrength.WEAK) 4 else 5
    else -> if (strength == GrainStrength.WEAK) 2 else 3
}

private fun WhiteBalance.applyTo(profile: RawConvProfile): RawConvProfile {
    var p = profile
    mode?.let { p = p.with(RawConvProfile.WHITE_BALANCE, it.nativeValue()) }
    kelvin?.let { p = p.with(RawConvProfile.WB_COLOR_TEMP, it) }
    shiftR?.let { p = p.with(RawConvProfile.WB_SHIFT_R, it) }
    shiftB?.let { p = p.with(RawConvProfile.WB_SHIFT_B, it) }
    return p
}

private fun WhiteBalanceMode.nativeValue(): Int = when (this) {
    WhiteBalanceMode.AUTO -> 0x0002
    WhiteBalanceMode.DAYLIGHT -> 0x0004
    WhiteBalanceMode.INCANDESCENT -> 0x0006
    WhiteBalanceMode.SHADE -> 0x8006
    WhiteBalanceMode.KELVIN -> 0x8007
}
