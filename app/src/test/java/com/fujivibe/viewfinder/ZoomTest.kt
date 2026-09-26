package com.fujivibe.viewfinder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ZoomTest {

    @Test
    fun `pinch scales the current ratio`() {
        assertEquals(3f, pinchedZoomRatio(current = 2f, scaleFactor = 1.5f, min = 1f, max = 10f))
    }

    @Test
    fun `pinch is clamped to the camera's max`() {
        assertEquals(8f, pinchedZoomRatio(current = 6f, scaleFactor = 2f, min = 1f, max = 8f))
    }

    @Test
    fun `pinch is clamped to the camera's min`() {
        assertEquals(1f, pinchedZoomRatio(current = 1.2f, scaleFactor = 0.5f, min = 1f, max = 8f))
    }

    @Test
    fun `readout shows one decimal place`() {
        assertEquals("2.3x", zoomReadout(2.34f))
        assertEquals("0.6x", zoomReadout(0.6f))
    }

    @Test
    fun `readout is shown at 1x too`() {
        assertEquals("1x", zoomReadout(1f))
        assertEquals("1x", zoomReadout(1.04f))
    }

    @Test
    fun `readout drops a trailing point-zero`() {
        assertEquals("2x", zoomReadout(2f))
        assertEquals("10x", zoomReadout(10f))
    }

    @Test
    fun `slider fraction is 0 at the minimum and 1 at the maximum`() {
        assertEquals(0f, zoomToSliderFraction(ratio = 0.5f, min = 0.5f, max = 8f), 1e-5f)
        assertEquals(1f, zoomToSliderFraction(ratio = 8f, min = 0.5f, max = 8f), 1e-5f)
    }

    @Test
    fun `slider is logarithmic, so the geometric mean sits at the midpoint`() {
        assertEquals(0.5f, zoomToSliderFraction(ratio = 4f, min = 1f, max = 16f), 1e-5f)
        assertEquals(4f, sliderFractionToZoom(fraction = 0.5f, min = 1f, max = 16f), 1e-4f)
    }

    @Test
    fun `slider mapping round-trips`() {
        for (ratio in listOf(0.5f, 1f, 2.3f, 5f, 8f)) {
            val fraction = zoomToSliderFraction(ratio, min = 0.5f, max = 8f)
            assertEquals(ratio, sliderFractionToZoom(fraction, min = 0.5f, max = 8f), 1e-3f)
        }
    }

    @Test
    fun `slider mapping stays in range for out-of-range input`() {
        assertEquals(0f, zoomToSliderFraction(ratio = 0.1f, min = 1f, max = 8f), 1e-5f)
        assertEquals(8f, sliderFractionToZoom(fraction = 1.5f, min = 1f, max = 8f), 1e-4f)
    }

    @Test
    fun `slider mapping is safe when the camera has no zoom range`() {
        assertEquals(0f, zoomToSliderFraction(ratio = 1f, min = 1f, max = 1f))
        assertEquals(1f, sliderFractionToZoom(fraction = 0.7f, min = 1f, max = 1f))
    }
}
