package com.fujivibe.review

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReviewZoomTest {

    // A 1000x2000 view showing a landscape photo: it fills the width and leaves bars above and below.
    private val viewW = 1000f
    private val viewH = 2000f
    private val landscape = fittedSize(viewW, viewH, bitmapWidth = 4000, bitmapHeight = 3000)

    private fun ReviewZoom.pinch(
        factor: Float,
        atX: Float = viewW / 2f,
        atY: Float = viewH / 2f,
        panX: Float = 0f,
        panY: Float = 0f,
        content: FittedSize = landscape,
    ) = transformedBy(factor, panX, panY, atX, atY, viewW, viewH, content)

    @Test
    fun `fittedSize fills the limiting side of the view`() {
        assertEquals(FittedSize(1000f, 750f), fittedSize(1000f, 2000f, 4000, 3000))
        assertEquals(FittedSize(1000f, 1500f), fittedSize(1000f, 1500f, 2000, 3000))
        assertEquals(FittedSize(500f, 2000f), fittedSize(1000f, 2000f, 1000, 4000))
    }

    @Test
    fun `starts fitted and not zoomed`() {
        assertFalse(ReviewZoom.FIT.isZoomed)
        assertEquals(1f, ReviewZoom.FIT.scale)
    }

    @Test
    fun `pinching in about the centre scales without moving the photo`() {
        val zoomed = ReviewZoom.FIT.pinch(2f)

        assertEquals(2f, zoomed.scale, 1e-5f)
        assertEquals(0f, zoomed.offsetX, 1e-4f)
        assertEquals(0f, zoomed.offsetY, 1e-4f)
        assertTrue(zoomed.isZoomed)
    }

    @Test
    fun `the point under the fingers stays under them while pinching`() {
        val start = ReviewZoom(scale = 2f)
        val centroidX = 700f
        val centroidY = 900f

        val zoomed = start.pinch(1.5f, atX = centroidX, atY = centroidY)

        // Screen position of a photo point q is centre + offset + scale * (q - centre). Pick the
        // point that was under the fingers before, and check it is still under them afterwards.
        val centreX = viewW / 2f
        val centreY = viewH / 2f
        val qX = centreX + (centroidX - centreX - start.offsetX) / start.scale
        val qY = centreY + (centroidY - centreY - start.offsetY) / start.scale
        assertEquals(centroidX, centreX + zoomed.offsetX + zoomed.scale * (qX - centreX), 1e-2f)
        assertEquals(centroidY, centreY + zoomed.offsetY + zoomed.scale * (qY - centreY), 1e-2f)
    }

    @Test
    fun `scale is clamped between fit and the maximum`() {
        assertEquals(ReviewZoom.MAX_SCALE, ReviewZoom.FIT.pinch(100f).scale, 1e-5f)
        assertEquals(ReviewZoom.FIT, ReviewZoom.FIT.pinch(0.2f))
    }

    @Test
    fun `pinching back out to fit resets the offset exactly`() {
        val panned = ReviewZoom.FIT.pinch(3f, panX = 200f, panY = 100f)
        assertTrue(panned.offsetX != 0f)

        assertEquals(ReviewZoom.FIT, panned.pinch(0.1f))
    }

    @Test
    fun `a sliver of zoom snaps back to fit`() {
        assertEquals(ReviewZoom.FIT, ReviewZoom.FIT.pinch(1.01f))
    }

    @Test
    fun `panning is clamped to the edges of the zoomed photo`() {
        val zoomed = ReviewZoom.FIT.pinch(2f)

        val farRight = zoomed.pinch(1f, panX = 10_000f)

        // 1000px-wide photo at 2x is 2000px, in a 1000px view: 500px of travel each way.
        assertEquals(500f, farRight.offsetX, 1e-3f)
        assertEquals(-500f, zoomed.pinch(1f, panX = -10_000f).offsetX, 1e-3f)
    }

    @Test
    fun `an axis the zoomed photo does not overflow stays centred`() {
        // The landscape photo is 750px tall in a 2000px view; at 2x it is 1500px, still shorter.
        val zoomed = ReviewZoom.FIT.pinch(2f)

        val dragged = zoomed.pinch(1f, panY = 800f)

        assertEquals(0f, dragged.offsetY, 1e-3f)
    }

    @Test
    fun `a non-finite step is ignored instead of poisoning the offsets with NaN`() {
        // Compose reports an unspecified (NaN) centroid when the last finger lifts; applying it
        // used to blank the photo.
        val zoomed = ReviewZoom.FIT.pinch(2f, panX = 100f)

        assertEquals(zoomed, zoomed.pinch(1f, atX = Float.NaN, atY = Float.NaN))
        assertEquals(zoomed, zoomed.pinch(Float.NaN))
        assertEquals(zoomed, zoomed.pinch(1f, panX = Float.POSITIVE_INFINITY))
        assertTrue(zoomed.offsetX.isFinite() && zoomed.offsetY.isFinite())
    }

    @Test
    fun `double-tap zooms in about the tap, and out again`() {
        val zoomedIn = ReviewZoom.FIT.doubleTapped(800f, 1000f, viewW, viewH, landscape)

        assertEquals(ReviewZoom.DOUBLE_TAP_SCALE, zoomedIn.scale, 1e-5f)
        assertTrue(zoomedIn.offsetX < 0f, "tapping right of centre should shift the photo left")

        assertEquals(ReviewZoom.FIT, zoomedIn.doubleTapped(800f, 1000f, viewW, viewH, landscape))
    }
}
