package com.fujivibe.capture

import androidx.exifinterface.media.ExifInterface
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

/**
 * Reads a [ShootingInfo] out of EXIF. [width] and [height] are passed in because they come from
 * decoding the JPEG itself: EXIF size tags aren't reliably present. Shared by a fresh Capture and
 * a saved Export, whose camera details `copyCameraMetadata` carries across.
 */
fun ExifInterface.toShootingInfo(width: Int, height: Int): ShootingInfo = ShootingInfo(
    iso = getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, -1).takeIf { it > 0 },
    exposureTimeSeconds = getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, -1.0).takeIf { it > 0 },
    exposureBiasStops = getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)
        ?.let { getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, 0.0) },
    zoomRatio = getAttributeDouble(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, -1.0).takeIf { it > 0 },
    width = width,
    height = height,
)
