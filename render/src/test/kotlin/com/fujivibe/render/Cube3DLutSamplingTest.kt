package com.fujivibe.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Cube3DLutSamplingTest {

    @Test
    fun `sample at an exact grid point returns that grid point's value unchanged`() {
        val lut = twoByTwoByTwoLut()

        assertRgbEquals(Rgb(0f, 0f, 0f), lut.sample(0f, 0f, 0f))
        assertRgbEquals(Rgb(1f, 0f, 0f), lut.sample(1f, 0f, 0f))
        assertRgbEquals(Rgb(1f, 1f, 1f), lut.sample(1f, 1f, 1f))
    }

    @Test
    fun `sample between grid points trilinearly blends the eight surrounding corners`() {
        // Corner R values (G, B held at 0), in the .cube spec's red-fastest order:
        // c000=0 c100=10 c010=20 c110=30 c001=40 c101=50 c011=60 c111=70
        val lut = Cube3DLut(
            size = 2,
            table = floatArrayOf(
                0f, 0f, 0f,
                10f, 0f, 0f,
                20f, 0f, 0f,
                30f, 0f, 0f,
                40f, 0f, 0f,
                50f, 0f, 0f,
                60f, 0f, 0f,
                70f, 0f, 0f,
            ),
        )

        // Hand-computed via the standard trilinear formula at (rt=0.25, gt=0.5, bt=0.75):
        // c00=2.5 c10=22.5 c01=42.5 c11=62.5 -> c0=12.5 c1=52.5 -> result=42.5
        val result = lut.sample(0.25f, 0.5f, 0.75f)

        assertEquals(42.5f, result.r, 1e-4f)
        assertEquals(0f, result.g, 1e-4f)
        assertEquals(0f, result.b, 1e-4f)
    }

    private fun twoByTwoByTwoLut(): Cube3DLut = Cube3DLut(
        size = 2,
        table = floatArrayOf(
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f,
            1f, 1f, 0f,
            0f, 0f, 1f,
            1f, 0f, 1f,
            0f, 1f, 1f,
            1f, 1f, 1f,
        ),
    )

    private fun assertRgbEquals(expected: Rgb, actual: Rgb, delta: Float = 1e-4f) {
        assertEquals(expected.r, actual.r, delta)
        assertEquals(expected.g, actual.g, delta)
        assertEquals(expected.b, actual.b, delta)
    }
}
