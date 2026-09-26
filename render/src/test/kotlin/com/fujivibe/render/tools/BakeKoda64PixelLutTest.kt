package com.fujivibe.render.tools

import com.fujivibe.render.Cube3DLut
import com.fujivibe.render.Rgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BakeKoda64PixelLutTest {

    private fun identityLut(size: Int): Cube3DLut {
        val table = FloatArray(size * size * size * 3)
        var i = 0
        val max = (size - 1).toFloat()
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            table[i++] = r / max
            table[i++] = g / max
            table[i++] = b / max
        }
        return Cube3DLut(size, table)
    }

    @Test
    fun `srgbToLinear matches the sRGB transfer function at known points`() {
        assertEquals(0f, BakeKoda64PixelLut.srgbToLinear(0f), 1e-6f)
        assertEquals(1f, BakeKoda64PixelLut.srgbToLinear(1f), 1e-6f)
        assertEquals(0.2140f, BakeKoda64PixelLut.srgbToLinear(0.5f), 1e-3f)
    }

    @Test
    fun `with full tone kept, baking linearizes each grid coordinate before sampling, then corrects`() {
        // Identity source: sampling at the linearized coordinate returns it unchanged, so a
        // mid-grid entry (encoded 0.5) must equal correct() of the linear value, not of 0.5.
        val baked = BakeKoda64PixelLut.bake(identityLut(3), toneKeep = 1f)

        val linearMid = BakeKoda64PixelLut.srgbToLinear(0.5f)
        val expected = Koda64PixelCorrection.correct(Rgb(linearMid, linearMid, linearMid))
        val actual = baked.valueAt(1, 1, 1)
        assertEquals(expected.r, actual.r, 1e-5f)
        assertEquals(expected.g, actual.g, 1e-5f)
        assertEquals(expected.b, actual.b, 1e-5f)
    }

    @Test
    fun `with no tone kept, the source LUT's own tone curve is divided back out`() {
        // Identity-on-linear source has neutral-axis tone T(e) = linear(e); neutralizing must
        // return each grid point to its own encoded value, so only Koda64PixelCorrection remains.
        val baked = BakeKoda64PixelLut.bake(identityLut(5), toneKeep = 0f)

        for (i in 0 until 5) {
            val encoded = i / 4f
            val expected = Koda64PixelCorrection.correct(Rgb(encoded, encoded, encoded))
            val actual = baked.valueAt(i, i, i)
            assertEquals(expected.r, actual.r, 2e-3f)
            assertEquals(expected.g, actual.g, 2e-3f)
            assertEquals(expected.b, actual.b, 2e-3f)
        }
    }
}
