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

    fun loadFullResolution(): Bitmap? {
        if (!file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return null
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
