package com.fujivibe.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt

private const val TAG = "CaptureStore"

/**
 * Holds the app's single in-flight temp Capture. The backing file is fixed
 * and overwritten by each new shot — there is no queue or history.
 */
class CaptureStore(context: Context) {

    private val file = File(context.cacheDir, "capture.jpg")

    fun fileForNewCapture(): File {
        if (file.exists()) file.delete()
        return file
    }

    fun loadFullResolution(): Bitmap? = decode(BitmapFactory.Options())

    /** The Capture's file, for copying its camera metadata onto an Export; null if there is none. */
    fun metadataSource(): File? = file.takeIf { it.exists() }

    /**
     * Adds what only FujiVibe knows to the Capture's EXIF: the zoom it was taken at and, for a
     * manual exposure, how much brighter or darker it is than the camera's metering.
     * The camera already records ISO, shutter, aperture and date itself.
     */
    fun recordShootingSettings(zoomRatio: Float, exposureBiasStops: Double?) {
        if (!file.exists()) return
        try {
            ExifInterface(file.path).apply {
                // ExifInterface parses this tag as a decimal and rejects "a/b" fractions.
                setAttribute(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, String.format(Locale.US, "%.2f", zoomRatio))
                if (exposureBiasStops != null) {
                    setAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, "${(exposureBiasStops * 100).roundToInt()}/100")
                }
                saveAttributes()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't record shooting settings", e)
        }
    }

    /** What the Capture was shot with, read back from its EXIF; null if there's no Capture. */
    fun shootingInfo(): ShootingInfo? {
        if (!file.exists()) return null
        return try {
            val exif = ExifInterface(file.path)
            // Measured from the JPEG itself; EXIF size tags aren't reliably present.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            ShootingInfo(
                iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, -1).takeIf { it > 0 },
                exposureTimeSeconds = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, -1.0).takeIf { it > 0 },
                exposureBiasStops = exif.getAttribute(ExifInterface.TAG_EXPOSURE_BIAS_VALUE)
                    ?.let { exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, 0.0) },
                zoomRatio = exif.getAttributeDouble(ExifInterface.TAG_DIGITAL_ZOOM_RATIO, -1.0).takeIf { it > 0 },
                width = bounds.outWidth,
                height = bounds.outHeight,
            )
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't read shooting settings", e)
            null
        }
    }

    /**
     * A downscaled decode of the same Capture, so Review's per-swipe LUT render stays fast.
     * [maxDimension] bounds the longer edge; the source is decoded at the nearest cheaper
     * power-of-two sample size rather than full resolution.
     *
     * On-device measurement: a typical ~4000px-edge capture at the old 1024 cap landed just
     * above a power-of-two boundary and decoded at ~2040px (~3.1MP) - CPU trilinear LUT
     * interpolation over that many pixels took ~3.2s per swipe, nowhere near "instant." 480
     * reliably lands in the ~510px bracket (~0.2MP), which renders in a couple hundred ms.
     */
    fun loadPreview(maxDimension: Int = 480): Bitmap? {
        if (!file.exists()) return null

        // inJustDecodeBounds decodes no pixels and always returns null - only outWidth/outHeight
        // are populated - so this probe can't go through the decode() helper below.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (longestEdge / (sampleSize * 2) >= maxDimension) sampleSize *= 2

        return decode(BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    private fun decode(options: BitmapFactory.Options): Bitmap? {
        if (!file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
        return bitmap.correctedForExifOrientation(file)
    }

    fun discard() {
        if (file.exists()) file.delete()
    }
}

/**
 * CameraX writes capture orientation as an EXIF tag rather than rotating the
 * pixel data itself; BitmapFactory ignores that tag, so a bitmap decoded
 * straight from the file looks rotated unless corrected here.
 */
private fun Bitmap.correctedForExifOrientation(file: File): Bitmap {
    val orientation = ExifInterface(file.path)
        .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

    // All eight EXIF orientations: mirrored selfies combine a flip with a rotation
    // (TRANSPOSE / TRANSVERSE), so rotation-only handling would leave them sideways.
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            matrix.setRotate(180f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(-90f)
            matrix.postScale(-1f, 1f)
        }
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
