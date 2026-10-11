package com.fujivibe.review

/** The size of the photo as drawn on screen, in pixels (the Fit-scaled bitmap, not the screen). */
data class FittedSize(val width: Float, val height: Float)

/** How a [bitmapWidth] x [bitmapHeight] image is drawn inside a [viewWidth] x [viewHeight] view with Fit scaling. */
fun fittedSize(viewWidth: Float, viewHeight: Float, bitmapWidth: Int, bitmapHeight: Int): FittedSize {
    if (bitmapWidth <= 0 || bitmapHeight <= 0) return FittedSize(viewWidth, viewHeight)
    val fit = minOf(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
    return FittedSize(bitmapWidth * fit, bitmapHeight * fit)
}

/**
 * Pinch-zoom and pan state for the photo on Review, applied as a transform about the centre of the
 * view: [scale] 1 is the whole photo fitted to the screen, and the offsets are the translation in
 * screen pixels. Pure arithmetic with no Compose types, so it can be unit-tested like `Zoom.kt`.
 *
 * Every transform is clamped, so the photo can never be pushed off-screen: the scale stays in
 * `1..MAX_SCALE`, and the offset stays within the edges of the zoomed photo (an axis the zoomed
 * photo doesn't yet overflow stays centred).
 */
data class ReviewZoom(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
) {
    /** True once the photo is meaningfully magnified; one-finger drags pan instead of swiping looks. */
    val isZoomed: Boolean get() = scale > 1f

    /**
     * This state after a pinch/pan step: [zoomChange] is the multiplicative scale change and
     * ([panX], [panY]) how far the touch centroid moved, with the centroid now at
     * ([centroidX], [centroidY]) relative to the view's top-left. The point of the photo under the
     * centroid stays under it, which is what makes the pinch feel anchored to the fingers.
     */
    fun transformedBy(
        zoomChange: Float,
        panX: Float,
        panY: Float,
        centroidX: Float,
        centroidY: Float,
        viewWidth: Float,
        viewHeight: Float,
        content: FittedSize,
    ): ReviewZoom {
        // A non-finite value (e.g. the "unspecified" centroid Compose reports when the last finger
        // lifts) would poison the offsets with NaN, and a NaN translation draws nothing: a black
        // screen. Ignore such a step rather than apply it.
        if (!(zoomChange.isFinite() && panX.isFinite() && panY.isFinite() &&
                centroidX.isFinite() && centroidY.isFinite())
        ) {
            return this
        }
        val newScale = (scale * zoomChange).coerceIn(1f, MAX_SCALE)
        // Pinching all the way back out returns to the plain fitted photo, exactly.
        if (newScale <= SNAP_TO_FIT_BELOW) return FIT

        val ratio = newScale / scale
        val fromCentreX = centroidX - viewWidth / 2f
        val fromCentreY = centroidY - viewHeight / 2f
        return ReviewZoom(
            newScale,
            fromCentreX + panX - ratio * (fromCentreX - offsetX),
            fromCentreY + panY - ratio * (fromCentreY - offsetY),
        ).clamped(viewWidth, viewHeight, content)
    }

    /** Double-tap: back to fit if already zoomed, otherwise in to [DOUBLE_TAP_SCALE] about the tap. */
    fun doubleTapped(
        tapX: Float,
        tapY: Float,
        viewWidth: Float,
        viewHeight: Float,
        content: FittedSize,
    ): ReviewZoom =
        if (isZoomed) {
            FIT
        } else {
            transformedBy(DOUBLE_TAP_SCALE, 0f, 0f, tapX, tapY, viewWidth, viewHeight, content)
        }

    private fun clamped(viewWidth: Float, viewHeight: Float, content: FittedSize): ReviewZoom {
        val maxX = maxOf(0f, (content.width * scale - viewWidth) / 2f)
        val maxY = maxOf(0f, (content.height * scale - viewHeight) / 2f)
        return copy(offsetX = offsetX.coerceIn(-maxX, maxX), offsetY = offsetY.coerceIn(-maxY, maxY))
    }

    companion object {
        val FIT = ReviewZoom()

        /** Far enough to pixel-peep a ~2000px detail render, without letting the photo turn to mush. */
        const val MAX_SCALE = 6f

        const val DOUBLE_TAP_SCALE = 2.5f

        /** A pinch ending this close to 1x counts as back to fit, so a sliver of zoom can't linger. */
        const val SNAP_TO_FIT_BELOW = 1.02f
    }
}
