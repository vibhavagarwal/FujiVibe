package com.fujivibe.render

import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * [RenderPipeline] decorator: renders through [inner] unchanged, then overlays a coarse,
 * monochromatic film-grain texture — but only for [GRAIN_ENABLED_SIMULATIONS], so most Film
 * Simulations pass through exactly as [inner] produced them. Never touches [inner] itself.
 */
class GrainRenderPipeline(
    private val inner: RenderPipeline,
) : RenderPipeline {

    override suspend fun render(source: PixelImage, selection: RenderSelection): PixelImage {
        val rendered = inner.render(source, selection)
        val filmSimulation = (selection as? RenderSelection.Simulation)?.filmSimulation
        if (filmSimulation !in GRAIN_ENABLED_SIMULATIONS) return rendered
        return applyGrain(rendered)
    }

    private fun applyGrain(image: PixelImage): PixelImage {
        // Grid density is expressed relative to image width, not a fixed pixel count, so grain
        // reads at the same relative coarseness whether rendering a downscaled Review preview
        // or a full-resolution Export.
        val cellsWide = GRAIN_CELLS_ACROSS_WIDTH + 1
        val cellsHigh = ceil(image.height.toFloat() / image.width * GRAIN_CELLS_ACROSS_WIDTH).toInt() + 1
        val noise = NoiseGrid.generate(cellsWide, cellsHigh, GRAIN_SEED)

        val widthDivisor = (image.width - 1).coerceAtLeast(1).toFloat()
        val heightDivisor = (image.height - 1).coerceAtLeast(1).toFloat()

        val outPixels = IntArray(image.pixels.size)
        for (y in 0 until image.height) {
            val v = y / heightDivisor
            for (x in 0 until image.width) {
                val u = x / widthDivisor
                val noiseValue = noise.sample(u, v)
                val index = y * image.width + x
                outPixels[index] = blendGrain(image.pixels[index], noiseValue)
            }
        }
        return PixelImage(image.width, image.height, outPixels)
    }

    private fun blendGrain(pixel: Int, noiseValue: Float): Int {
        val a = (pixel ushr 24) and 0xFF
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF

        val outR = blendChannel(r, noiseValue)
        val outG = blendChannel(g, noiseValue)
        val outB = blendChannel(b, noiseValue)

        return (a shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    private fun blendChannel(channel: Int, noiseValue: Float): Int {
        val base = channel / 255f
        val softLit = softLight(base, noiseValue)
        val blended = base + (softLit - base) * GRAIN_OPACITY
        return (blended.coerceIn(0f, 1f) * 255f).roundToInt()
    }

    /** Standard (W3C-compatible) soft-light blend: `base` is the backdrop, `blend` the source. */
    private fun softLight(base: Float, blend: Float): Float =
        if (blend <= 0.5f) {
            base - (1f - 2f * blend) * base * (1f - base)
        } else {
            base + (2f * blend - 1f) * (softLightD(base) - base)
        }

    private fun softLightD(base: Float): Float =
        if (base <= 0.25f) ((16f * base - 12f) * base + 4f) * base else sqrt(base)

    companion object {
        /** Higher = finer grain, lower = coarser. */
        const val GRAIN_CELLS_ACROSS_WIDTH = 40

        /** Blend-mode opacity, per spec's 4-7%. */
        const val GRAIN_OPACITY = 0.05f

        /** Fixed, not random-per-render: same photo always produces the same grain pattern. */
        const val GRAIN_SEED = 42L

        /** Which Film Simulations get grain. Deliberately a set, not one hardcoded check. */
        val GRAIN_ENABLED_SIMULATIONS = setOf(
            FilmSimulation.CLASSIC_NEG_PIXEL,
            FilmSimulation.NOSTALGIC_NEG_PIXEL,
            FilmSimulation.KODACHROME_64,
        )
    }
}
