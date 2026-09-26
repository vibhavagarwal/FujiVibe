package com.fujivibe.viewfinder

import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/**
 * The camera zoom ratio after one pinch step: [current] scaled by the gesture's [scaleFactor],
 * clamped to the camera's supported [min]..[max] range.
 */
fun pinchedZoomRatio(current: Float, scaleFactor: Float, min: Float, max: Float): Float =
    (current * scaleFactor).coerceIn(min, max)

/** A "2.3x" readout for the Viewfinder, shown at every zoom level (so "1x" when unzoomed). */
fun zoomReadout(ratio: Float): String {
    val text = String.format(Locale.US, "%.1f", ratio)
    return text.removeSuffix(".0") + "x"
}

/**
 * Where [ratio] sits on the zoom slider, `0..1`. Logarithmic, because zoom feels multiplicative:
 * a linear slider over e.g. 0.5x-30x would spend nearly all its travel above 10x.
 */
fun zoomToSliderFraction(ratio: Float, min: Float, max: Float): Float {
    if (max <= min) return 0f
    return (ln(ratio.coerceIn(min, max) / min) / ln(max / min)).coerceIn(0f, 1f)
}

/** Inverse of [zoomToSliderFraction]. */
fun sliderFractionToZoom(fraction: Float, min: Float, max: Float): Float {
    if (max <= min) return max
    return (min * (max / min).pow(fraction.coerceIn(0f, 1f))).coerceIn(min, max)
}
