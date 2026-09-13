package com.fujivibe.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verifies the Film Simulation registry is wired to the right `.cube` resources: Classic Neg.
 * (Pixel) and Nostalgic Neg. (Pixel) each to their own FujiVibe-derived `derived-luts/` file (per
 * ADR 0005/0006 and ADR 0007/0008 respectively). Both original, unmodified stock-LUT entries
 * (Classic Neg., Nostalgic Neg.) have been removed from the registry — see ADR 0006 and ADR 0008
 * — so this suite no longer touches `raw-assets/`'s `provia conversion luts/` files directly.
 *
 * `derived-luts/` is committed (not gitignored), so these tests don't need a skip-if-missing
 * guard the way the original `raw-assets/`-dependent version of this suite did.
 */
class ResourceLutLoaderWiringTest {

    @BeforeEach
    fun assumeRealLutFilesAreStaged() {
        val classLoader = javaClass.classLoader
        assumeTrue(
            classLoader.getResource(FilmSimulation.CLASSIC_NEG_PIXEL.cubeResourceName) != null &&
                classLoader.getResource(FilmSimulation.NOSTALGIC_NEG_PIXEL.cubeResourceName) != null,
            "derived-luts/ .cube files aren't staged locally (see ADR 0005/0007) — skipping",
        )
    }

    @Test
    fun `all registered Film Simulations load as real 32-point LUTs`() {
        val loader = ResourceLutLoader()

        for (simulation in FilmSimulation.entries) {
            val loaded = loader.load(simulation)

            assertEquals(32, loaded.size, "${simulation.name} did not load as a 32-point LUT")
        }
    }

    @Test
    fun `Nostalgic Neg Pixel loads as a real 32-point LUT`() {
        val loaded = ResourceLutLoader().load(FilmSimulation.NOSTALGIC_NEG_PIXEL)

        assertEquals(32, loaded.size)
    }

    @Test
    fun `Classic Neg Pixel and Nostalgic Neg Pixel are distinct LUTs`() {
        val loader = ResourceLutLoader()

        val classicNegPixel = loader.load(FilmSimulation.CLASSIC_NEG_PIXEL)
        val nostalgicNegPixel = loader.load(FilmSimulation.NOSTALGIC_NEG_PIXEL)

        // Same sample point, two different real files: results must differ, otherwise both
        // simulations are silently loading the same underlying LUT.
        assertNotEquals(classicNegPixel.sample(0.5f, 0.2f, 0.8f), nostalgicNegPixel.sample(0.5f, 0.2f, 0.8f))
    }
}
