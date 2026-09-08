package com.fujivibe.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

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

    /**
     * A downscaled decode of the same Capture, so Review's per-swipe LUT render stays fast.
     * [maxDimension] bounds the longer edge; the source is decoded at the nearest cheaper
     * power-of-two sample size rather than full resolution.
     */
    fun loadPreview(maxDimension: Int = 1024): Bitmap? {
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

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
