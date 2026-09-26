package com.fujivibe.viewfinder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `readout is hidden at 1x`() {
        assertNull(zoomReadout(1f))
        assertNull(zoomReadout(1.04f))
    }
}
