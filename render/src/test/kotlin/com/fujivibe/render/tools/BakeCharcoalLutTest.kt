package com.fujivibe.render.tools

import com.fujivibe.render.Rgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BakeCharcoalLutTest {

    @Test
    fun `bakes a cube of the requested size`() {
        assertEquals(5, BakeCharcoalLut.bake(5).size)
        assertEquals(BakeCharcoalLut.GRID_SIZE, BakeCharcoalLut.bake().size)
    }

    @Test
    fun `each grid point is the correction of its own sRGB-encoded coordinate`() {
        // Unlike the other derived LUTs there's no source cube, so no linearize step: the grid
        // coordinate is already the gamma-encoded pixel value the app feeds the LUT.
        val baked = BakeCharcoalLut.bake(5)

        for (r in 0 until 5) for (g in 0 until 5) for (b in 0 until 5) {
            val expected = CharcoalCorrection.correct(Rgb(r / 4f, g / 4f, b / 4f))
            val actual = baked.valueAt(r, g, b)
            assertEquals(expected.r, actual.r, 1e-6f)
            assertEquals(expected.g, actual.g, 1e-6f)
            assertEquals(expected.b, actual.b, 1e-6f)
        }
    }

    @Test
    fun `every baked entry is a neutral gray`() {
        val baked = BakeCharcoalLut.bake(6)

        for (r in 0 until 6) for (g in 0 until 6) for (b in 0 until 6) {
            val value = baked.valueAt(r, g, b)
            assertEquals(value.r, value.g, 1e-6f)
            assertEquals(value.g, value.b, 1e-6f)
        }
    }
}
