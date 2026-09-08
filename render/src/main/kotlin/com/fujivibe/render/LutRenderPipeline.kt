package com.fujivibe.render

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** [RenderPipeline] backed by CPU-side trilinear LUT interpolation, no GPU/OpenGL. */
class LutRenderPipeline(
    private val lutLoader: LutLoader = ResourceLutLoader(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : RenderPipeline {

    override suspend fun render(source: PixelImage, selection: RenderSelection): PixelImage =
        withContext(dispatcher) {
            when (selection) {
                RenderSelection.Original -> source
                is RenderSelection.Simulation -> applyLut(source, lutLoader.load(selection.filmSimulation))
            }
        }

    private fun applyLut(source: PixelImage, lut: Cube3DLut): PixelImage {
        val outPixels = IntArray(source.pixels.size)
        for (i in source.pixels.indices) {
            outPixels[i] = applyLutToPixel(source.pixels[i], lut)
        }
        return PixelImage(source.width, source.height, outPixels)
    }

    private fun applyLutToPixel(pixel: Int, lut: Cube3DLut): Int {
        val a = (pixel ushr 24) and 0xFF
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF

        val rendered = lut.sample(r / 255f, g / 255f, b / 255f)

        val outR = (rendered.r.coerceIn(0f, 1f) * 255f).roundToInt()
        val outG = (rendered.g.coerceIn(0f, 1f) * 255f).roundToInt()
        val outB = (rendered.b.coerceIn(0f, 1f) * 255f).roundToInt()

        return (a shl 24) or (outR shl 16) or (outG shl 8) or outB
    }
}
