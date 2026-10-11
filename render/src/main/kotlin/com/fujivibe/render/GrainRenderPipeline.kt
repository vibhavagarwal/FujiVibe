package com.fujivibe.render

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : RenderPipeline {

    override suspend fun render(source: PixelImage, selection: RenderSelection): PixelImage {
        val rendered = inner.render(source, selection)
        val filmSimulation = (selection as? RenderSelection.Simulation)?.filmSimulation
        val profile = GRAIN_PROFILES[filmSimulation] ?: return rendered
        // Off the caller's thread: Review calls this from the UI coroutine, and at zoom-detail
        // resolution the grain pass alone is long enough to stall the screen.
        return withContext(dispatcher) { applyGrain(rendered, profile) }
    }

    private suspend fun applyGrain(image: PixelImage, profile: GrainProfile): PixelImage {
        // Grid density is expressed relative to image width, not a fixed pixel count, so grain
        // reads at the same relative coarseness whether rendering a downscaled Review preview
        // or a full-resolution Export.
        val cellsWide = profile.cellsAcrossWidth + 1
        val cellsHigh = ceil(image.height.toFloat() / image.width * profile.cellsAcrossWidth).toInt() + 1
        val noise = NoiseGrid.generate(cellsWide, cellsHigh, GRAIN_SEED)

        val widthDivisor = (image.width - 1).coerceAtLeast(1).toFloat()
        val heightDivisor = (image.height - 1).coerceAtLeast(1).toFloat()

        val outPixels = IntArray(image.pixels.size)
        parallelBands(image.pixels.size) { band ->
            for (index in band) {
                val y = index / image.width
                val x = index - y * image.width
                val noiseValue = noise.sample(x / widthDivisor, y / heightDivisor)
                outPixels[index] = blendGrain(image.pixels[index], noiseValue, profile.opacity)
            }
        }
        return PixelImage(image.width, image.height, outPixels)
    }

    private fun blendGrain(pixel: Int, noiseValue: Float, opacity: Float): Int {
        val a = (pixel ushr 24) and 0xFF
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF

        val outR = blendChannel(r, noiseValue, opacity)
        val outG = blendChannel(g, noiseValue, opacity)
        val outB = blendChannel(b, noiseValue, opacity)

        return (a shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    private fun blendChannel(channel: Int, noiseValue: Float, opacity: Float): Int {
        val base = channel / 255f
        val softLit = softLight(base, noiseValue)
        val blended = base + (softLit - base) * opacity
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

    /**
     * One look's grain: [cellsAcrossWidth] (higher = finer grain, lower = coarser) and the
     * blend-mode [opacity] (higher = stronger).
     */
    data class GrainProfile(val cellsAcrossWidth: Int, val opacity: Float)

    companion object {
        /** Higher = finer grain, lower = coarser. */
        const val GRAIN_CELLS_ACROSS_WIDTH = 40

        /** Blend-mode opacity, per spec's 4-7%. */
        const val GRAIN_OPACITY = 0.05f

        /** Fixed, not random-per-render: same photo always produces the same grain pattern. */
        const val GRAIN_SEED = 42L

        private val DEFAULT_PROFILE = GrainProfile(GRAIN_CELLS_ACROSS_WIDTH, GRAIN_OPACITY)

        /**
         * Charcoal's recipe calls for Grain Strong/Small: many more cells than the default, so each
         * grain is a few pixels rather than a soft clump, at several times the default opacity.
         */
        val CHARCOAL_PROFILE = GrainProfile(cellsAcrossWidth = 900, opacity = 0.18f)

        /** Which Film Simulations get grain, and how. Deliberately a map, not one hardcoded check. */
        val GRAIN_PROFILES: Map<FilmSimulation, GrainProfile> = mapOf(
            FilmSimulation.CLASSIC_NEG_PIXEL to DEFAULT_PROFILE,
            FilmSimulation.NOSTALGIC_NEG_PIXEL to DEFAULT_PROFILE,
            FilmSimulation.KODA64_PIXEL to DEFAULT_PROFILE,
            FilmSimulation.CHARCOAL to CHARCOAL_PROFILE,
        )

        val GRAIN_ENABLED_SIMULATIONS: Set<FilmSimulation> = GRAIN_PROFILES.keys
    }
}
