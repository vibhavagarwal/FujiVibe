package com.fujivibe.render

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `every currently registered Film Simulation is grain-enabled`() {
        // Both original, unmodified stock-LUT entries (Classic Neg., Nostalgic Neg.) have been
        // removed from the registry (ADR 0006, ADR 0008), so there's no longer a real registry
        // member outside GRAIN_ENABLED_SIMULATIONS to exercise the "passes through untouched"
        // branch against. This test documents that fact and will fail — a deliberate prompt to
        // decide on grain, not an oversight — the moment a future Film Simulation is added
        // without an explicit grain decision.
        assertEquals(FilmSimulation.entries.toSet(), GrainRenderPipeline.GRAIN_ENABLED_SIMULATIONS)
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
    fun `Charcoal gets grain applied, changing at least one pixel`() = runTest {
        val source = solidImage(20, 20, 0xFF808080.toInt())
        val pipeline = GrainRenderPipeline(PassthroughPipeline)

        val result = pipeline.render(source, RenderSelection.Simulation(FilmSimulation.CHARCOAL))

        assertNotEquals(source, result)
    }

    @Test
    fun `Charcoal's grain is stronger and finer than the default`() {
        val default = GrainRenderPipeline.GRAIN_PROFILES.getValue(FilmSimulation.CLASSIC_NEG_PIXEL)
        val charcoal = GrainRenderPipeline.GRAIN_PROFILES.getValue(FilmSimulation.CHARCOAL)

        assertTrue(charcoal.opacity > default.opacity)
        assertTrue(charcoal.cellsAcrossWidth > default.cellsAcrossWidth)
    }

    @Test
    fun `stronger grain moves pixels further from the source`() = runTest {
        val source = solidImage(200, 200, 0xFF808080.toInt())
        val pipeline = GrainRenderPipeline(PassthroughPipeline)

        suspend fun meanDeviation(simulation: FilmSimulation): Double =
            pipeline.render(source, RenderSelection.Simulation(simulation)).pixels
                .map { Math.abs(((it ushr 8) and 0xFF) - 0x80) }.average()

        assertTrue(meanDeviation(FilmSimulation.CHARCOAL) > meanDeviation(FilmSimulation.CLASSIC_NEG_PIXEL))
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
