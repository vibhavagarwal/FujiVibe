package com.fujivibe.render.tools

import com.fujivibe.render.CubeLutParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BakeNostalgicNegPixelLutTest {

    @Test
    fun `baking runs every grid entry through NostalgicNegCorrection#correct`() {
        val sourceText = """
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            0.93 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()
        val source = CubeLutParser.parse(sourceText)

        val baked = BakeNostalgicNegPixelLut.bake(source)

        assertEquals(source.size, baked.size)
        for (r in 0 until source.size) {
            for (g in 0 until source.size) {
                for (b in 0 until source.size) {
                    val expected = NostalgicNegCorrection.correct(source.valueAt(r, g, b))
                    assertEquals(expected, baked.valueAt(r, g, b))
                }
            }
        }
    }
}
