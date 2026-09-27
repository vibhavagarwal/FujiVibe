package com.fujivibe.capture

import com.fujivibe.viewfinder.AspectChoice
import com.fujivibe.viewfinder.brightnessReadout
import com.fujivibe.viewfinder.shutterReadout
import com.fujivibe.viewfinder.zoomReadout
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * What a Capture was shot with, as read back from its EXIF: ISO and shutter as the camera
 * actually used them (Auto included), plus the zoom and manual brightness offset FujiVibe
 * records alongside. Any field the file lacks is null.
 */
data class ShootingInfo(
    val iso: Int?,
    val exposureTimeSeconds: Double?,
    val exposureBiasStops: Double?,
    val zoomRatio: Double?,
    val width: Int,
    val height: Int,
)

/** The photo's shape as a known aspect ratio, if it is one (within 1%). */
fun aspectOf(width: Int, height: Int): AspectChoice? {
    if (width <= 0 || height <= 0) return null
    val ratio = maxOf(width, height).toDouble() / minOf(width, height)
    return AspectChoice.entries.firstOrNull { abs(ratio - it.long.toDouble() / it.short) / ratio < 0.01 }
}

/** "ISO 400 · 1/250 · +0.3 EV · 2x · 3:2", like a camera's playback display. */
fun shootingInfoLine(info: ShootingInfo): String = listOfNotNull(
    info.iso?.let { "ISO $it" },
    info.exposureTimeSeconds?.let { shutterReadout((it * 1_000_000_000).roundToLong()) },
    info.exposureBiasStops?.takeIf { abs(it) >= 0.05 }?.let(::brightnessReadout),
    info.zoomRatio?.let { zoomReadout(it.toFloat()) },
    aspectOf(info.width, info.height)?.label,
).joinToString("  ·  ")
