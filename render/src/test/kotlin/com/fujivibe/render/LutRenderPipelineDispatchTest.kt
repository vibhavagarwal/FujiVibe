package com.fujivibe.render

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

class LutRenderPipelineDispatchTest {

    @Test
    fun `render executes LUT work on the configured background dispatcher, not the calling thread`() = runTest {
        val callingThreadName = Thread.currentThread().name
        var threadDuringLutLoad: String? = null
        val anyLut = Cube3DLut(size = 2, table = FloatArray(2 * 2 * 2 * 3))

        val backgroundExecutor = Executors.newSingleThreadExecutor { Thread(it, "render-background-worker") }
        val backgroundDispatcher = backgroundExecutor.asCoroutineDispatcher()
        try {
            val pipeline = LutRenderPipeline(
                lutLoader = {
                    threadDuringLutLoad = Thread.currentThread().name
                    anyLut
                },
                dispatcher = backgroundDispatcher,
            )
            val source = PixelImage(width = 1, height = 1, pixels = intArrayOf(0xFF000000.toInt()))

            pipeline.render(source, RenderSelection.Simulation(FilmSimulation.CLASSIC_NEG))

            assertEquals("render-background-worker", threadDuringLutLoad)
            assertNotEquals(callingThreadName, threadDuringLutLoad)
        } finally {
            backgroundExecutor.shutdown()
        }
    }
}
