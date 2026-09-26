package com.fujivibe.viewfinder

import java.util.Locale

/**
 * The camera zoom ratio after one pinch step: [current] scaled by the gesture's [scaleFactor],
 * clamped to the camera's supported [min]..[max] range.
 */
fun pinchedZoomRatio(current: Float, scaleFactor: Float, min: Float, max: Float): Float =
    (current * scaleFactor).coerceIn(min, max)

/** A "2.3x" readout for the Viewfinder, or null at 1.0x (unzoomed) where nothing is shown. */
fun zoomReadout(ratio: Float): String? {
    val text = String.format(Locale.US, "%.1fx", ratio)
    return if (text == "1.0x") null else text
}
