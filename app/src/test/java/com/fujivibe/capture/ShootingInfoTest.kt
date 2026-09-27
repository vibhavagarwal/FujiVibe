package com.fujivibe.capture

import com.fujivibe.viewfinder.AspectChoice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ShootingInfoTest {

    @Test
    fun `a full line reads like a camera playback display`() {
        val info = ShootingInfo(
            iso = 400,
            exposureTimeSeconds = 0.004,
            exposureBiasStops = 0.3,
            zoomRatio = 2.0,
            width = 2720,
            height = 4080,
        )
        assertEquals("ISO 400  ·  1/250  ·  +0.3 EV  ·  2x  ·  3:2", shootingInfoLine(info))
    }

    @Test
    fun `missing values and a zero offset are left out`() {
        val info = ShootingInfo(
            iso = 372,
            exposureTimeSeconds = 0.0333,
            exposureBiasStops = 0.0,
            zoomRatio = null,
            width = 3000,
            height = 4000,
        )
        assertEquals("ISO 372  ·  1/30  ·  4:3", shootingInfoLine(info))
    }

    @Test
    fun `aspect is recognised in either orientation and unknown shapes give none`() {
        assertEquals(AspectChoice.SIXTEEN_NINE, aspectOf(4000, 2250))
        assertEquals(AspectChoice.ONE_ONE, aspectOf(3000, 3000))
        assertNull(aspectOf(4000, 2000))
    }
}
