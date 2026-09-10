package com.fujivibe.render.tools

import com.fujivibe.render.CubeLutParser
import com.fujivibe.render.Rgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CubeLutWriterTest {

    @Test
    fun `writing then re-parsing a LUT reproduces its original grid values`() {
        val originalText = """
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
        val original = CubeLutParser.parse(originalText)

        val writtenText = CubeLutWriter.write(original)
        val roundTripped = CubeLutParser.parse(writtenText)

        assertEquals(original.size, roundTripped.size)
        for (r in 0 until original.size) {
            for (g in 0 until original.size) {
                for (b in 0 until original.size) {
                    assertEquals(original.valueAt(r, g, b), roundTripped.valueAt(r, g, b))
                }
            }
        }
    }
}
