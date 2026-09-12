package com.fujivibe.render

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class GrainRenderPipelineTest {

    private object PassthroughPipeline : RenderPipeline {
        override suspend fun render(source: PixelImage, selection: RenderSelection): PixelImage = source
    }

    private fun solidImage(width: Int, height: Int, argb: Int): PixelImage =
        PixelImage(width, height, IntArray(width * height) { argb })

    @Test
    fun `Original passes through untouched, no grain applied`() = runTest {
        val source = solidImage(20, 20, 0xFF808080.toInt())
        val inner = PassthroughPipeline
        val pipeline = GrainRenderPipeline(inner)

        val result = pipeline.render(source, RenderSelection.Original)

        assertEquals(source, result)
    }

    @Test
    fun `a Film Simulation not in the grain-enabled set passes through untouched`() = runTest {
        val source = solidImage(20, 20, 0xFF808080.toInt())
        val inner = PassthroughPipeline
        val pipeline = GrainRenderPipeline(inner)

        val result = pipeline.render(source, RenderSelection.Simulation(FilmSimulation.NOSTALGIC_NEG))

        assertEquals(source, result)
    }

    @Test
    fun `Classic Neg Pixel gets grain applied, changing at least one pixel`() = runTest {
        val source = solidImage(20, 20, 0xFF808080.toInt())
        val inner = PassthroughPipeline
        val pipeline = GrainRenderPipeline(inner)

        val result = pipeline.render(source, RenderSelection.Simulation(FilmSimulation.CLASSIC_NEG_PIXEL))

        assertNotEquals(source, result)
    }

    @Test
    fun `Nostalgic Neg Pixel gets grain applied, changing at least one pixel`() = runTest {
        val source = solidImage(20, 20, 0xFF808080.toInt())
        val inner = PassthroughPipeline
        val pipeline = GrainRenderPipeline(inner)

        val result = pipeline.render(source, RenderSelection.Simulation(FilmSimulation.NOSTALGIC_NEG_PIXEL))

        assertNotEquals(source, result)
    }

    @Test
    fun `grain preserves each pixel's alpha channel exactly`() = runTest {
        val source = solidImage(20, 20, 0x80808080.toInt())
        val inner = PassthroughPipeline
        val pipeline = GrainRenderPipeline(inner)

        val result = pipeline.render(source, RenderSelection.Simulation(FilmSimulation.CLASSIC_NEG_PIXEL))

        for (pixel in result.pixels) {
            assertEquals(0x80, (pixel ushr 24) and 0xFF)
        }
    }

    @Test
    fun `grain is deterministic, rendering the same image twice gives identical output`() = runTest {
        val source = solidImage(20, 20, 0xFF808080.toInt())
        val inner = PassthroughPipeline
        val pipeline = GrainRenderPipeline(inner)
        val selection = RenderSelection.Simulation(FilmSimulation.CLASSIC_NEG_PIXEL)

        val first = pipeline.render(source, selection)
        val second = pipeline.render(source, selection)

        assertEquals(first, second)
    }
}
