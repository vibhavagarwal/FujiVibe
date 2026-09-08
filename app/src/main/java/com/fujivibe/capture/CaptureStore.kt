package com.fujivibe.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    fun loadFullResolution(): Bitmap? =
        if (file.exists()) BitmapFactory.decodeFile(file.path) else null

    fun discard() {
        if (file.exists()) file.delete()
    }
}
