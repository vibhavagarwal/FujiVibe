package com.fujivibe.viewfinder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExposureTest {

    private val isoRange = 50..6400
    private val timeRange = 10_000L..MAX_MANUAL_EXPOSURE_NS
    private val metered = Exposure(iso = 400, exposureTimeNs = 10_000_000L) // ISO 400 at 1/100

    @Test
    fun `iso stops include the camera's own min and max and standard values between`() {
        val stops = isoStops(44, 7000)
        assertEquals(44, stops.first())
        assertEquals(7000, stops.last())
        assertTrue(100 in stops && 6400 in stops)
        assertEquals(stops.sorted(), stops)
    }

    @Test
    fun `shutter stops stay within the camera's range and stop at one second`() {
        val stops = shutterStops(minNs = 1_000_000L / 4, maxNs = 30_000_000_000L)
        assertEquals(1_000_000_000L / 4000, stops.first())
        assertEquals(MAX_MANUAL_EXPOSURE_NS, stops.last())
        assertEquals(stops.sorted(), stops)
    }

    @Test
    fun `shutter readout uses fractions below a third of a second and seconds above`() {
        assertEquals("1/250", shutterReadout(1_000_000_000L / 250))
        assertEquals("1/4", shutterReadout(250_000_000L))
        assertEquals("0.5s", shutterReadout(500_000_000L))
        assertEquals("1s", shutterReadout(1_000_000_000L))
    }

    @Test
    fun `both on auto leaves the camera on automatic exposure`() {
        assertNull(resolveExposure(null, null, metered, isoRange, timeRange))
    }

    @Test
    fun `both set is fully manual`() {
        assertEquals(
            Exposure(800, 2_000_000L),
            resolveExposure(800, 2_000_000L, metered, isoRange, timeRange),
        )
    }

    @Test
    fun `setting iso derives a shutter that keeps the metered brightness`() {
        // Twice the ISO needs half the time: ISO 800 at 1/200.
        assertEquals(
            Exposure(800, 5_000_000L),
            resolveExposure(800, null, metered, isoRange, timeRange),
        )
    }

    @Test
    fun `setting shutter derives an iso that keeps the metered brightness`() {
        // A quarter of the time needs four times the ISO: ISO 1600 at 1/400.
        assertEquals(
            Exposure(1600, 2_500_000L),
            resolveExposure(null, 2_500_000L, metered, isoRange, timeRange),
        )
    }

    @Test
    fun `a derived value is clamped to what the camera supports`() {
        // 1/8000 would need ISO 32000; the camera tops out at 6400.
        assertEquals(6400, resolveExposure(null, 125_000L, metered, isoRange, timeRange)!!.iso)
    }
}
