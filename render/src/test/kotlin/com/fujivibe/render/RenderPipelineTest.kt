package com.fujivibe.render

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RenderPipelineTest {

    @Test
    fun `selecting Original returns the source image byte-for-byte unchanged`() = runTest {
        val source = PixelImage(
            width = 2,
            height = 1,
            pixels = intArrayOf(0xFF112233.toInt(), 0xFF445566.toInt()),
        )
        val pipeline: RenderPipeline = LutRenderPipeline(
            lutLoader = { error("Original must not load a LUT") },
        )

        val result = pipeline.render(source, RenderSelection.Original)

        assertEquals(source, result)
    }

    @Test
    fun `applying a Film Simulation transforms every pixel through the loaded LUT, preserving alpha`() = runTest {
        // R inverted (1 - r), G and B pass through: sample(r, g, b) = (1 - r, g, b) exactly,
        // since inversion and pass-through are both linear across a 2-point grid.
        val invertRedLut = Cube3DLut(
            size = 2,
            table = floatArrayOf(
                1f, 0f, 0f, // r=0 g=0 b=0
                0f, 0f, 0f, // r=1 g=0 b=0
                1f, 1f, 0f, // r=0 g=1 b=0
                0f, 1f, 0f, // r=1 g=1 b=0
                1f, 0f, 1f, // r=0 g=0 b=1
                0f, 0f, 1f, // r=1 g=0 b=1
                1f, 1f, 1f, // r=0 g=1 b=1
                0f, 1f, 1f, // r=1 g=1 b=1
            ),
        )
        val pipeline: RenderPipeline = LutRenderPipeline(lutLoader = { invertRedLut })

        // Opaque red -> (1-1, 0, 0) = black. Half-alpha cyan -> (1-0, 1, 1) = white, alpha kept.
        val opaqueRed = 0xFFFF0000.toInt()
        val halfAlphaCyan = 0x8000FFFF.toInt()
        val expectedBlack = 0xFF000000.toInt()
        val expectedWhite = 0x80FFFFFF.toInt()

        for ((width, height) in listOf(2 to 1, 4 to 4)) {
            val pixels = IntArray(width * height) { index ->
                if (index % 2 == 0) opaqueRed else halfAlphaCyan
            }
            val source = PixelImage(width, height, pixels)

            val result = pipeline.render(source, RenderSelection.Simulation(FilmSimulation.CLASSIC_NEG))

            val expectedPixels = IntArray(width * height) { index ->
                if (index % 2 == 0) expectedBlack else expectedWhite
            }
            assertEquals(PixelImage(width, height, expectedPixels), result)
        }
    }
}
