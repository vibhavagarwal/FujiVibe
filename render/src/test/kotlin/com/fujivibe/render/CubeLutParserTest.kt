package com.fujivibe.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CubeLutParserTest {

    @Test
    fun `parses LUT_3D_SIZE and grid values in red-fastest order`() {
        // A minimal, hand-authored 2x2x2 cube: 8 grid points, each a distinct RGB triple,
        // written in the .cube spec's red-fastest, then green, then blue order.
        val cubeText = """
            # comment lines and blank lines must be ignored

            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()

        val lut = CubeLutParser.parse(cubeText)

        assertEquals(2, lut.size)
        assertEquals(Rgb(0f, 0f, 0f), lut.valueAt(r = 0, g = 0, b = 0))
        assertEquals(Rgb(1f, 0f, 0f), lut.valueAt(r = 1, g = 0, b = 0))
        assertEquals(Rgb(0f, 1f, 0f), lut.valueAt(r = 0, g = 1, b = 0))
        assertEquals(Rgb(1f, 1f, 0f), lut.valueAt(r = 1, g = 1, b = 0))
        assertEquals(Rgb(0f, 0f, 1f), lut.valueAt(r = 0, g = 0, b = 1))
        assertEquals(Rgb(1f, 0f, 1f), lut.valueAt(r = 1, g = 0, b = 1))
        assertEquals(Rgb(0f, 1f, 1f), lut.valueAt(r = 0, g = 1, b = 1))
        assertEquals(Rgb(1f, 1f, 1f), lut.valueAt(r = 1, g = 1, b = 1))
    }

    @Test
    fun `rejects a data line count that does not match LUT_3D_SIZE cubed`() {
        val truncatedCubeText = """
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
        """.trimIndent()

        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            CubeLutParser.parse(truncatedCubeText)
        }
    }

    @Test
    fun `rejects a non-numeric LUT_3D_SIZE with a descriptive error, not a raw parse exception`() {
        val cubeTextWithBadSize = "LUT_3D_SIZE abc"

        val exception = org.junit.jupiter.api.assertThrows<IllegalStateException> {
            CubeLutParser.parse(cubeTextWithBadSize)
        }
        assertEquals("Invalid LUT_3D_SIZE value: \"abc\"", exception.message)
    }
}
