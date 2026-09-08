package com.fujivibe.bitmap

import android.graphics.Bitmap
import com.fujivibe.render.PixelImage

/**
 * Adapts between Android's [Bitmap] and the render pipeline's platform-agnostic [PixelImage].
 * Lives outside any single feature package (`ui.review`, `gallery`, ...) since both the Review
 * preview and the Export gallery write need this same conversion.
 */
fun Bitmap.toPixelImage(): PixelImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return PixelImage(width, height, pixels)
}

fun PixelImage.toBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}
