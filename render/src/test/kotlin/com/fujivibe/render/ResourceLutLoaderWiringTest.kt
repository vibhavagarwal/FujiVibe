package com.fujivibe.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verifies the Film Simulation registry is wired to the right `.cube` resources: Nostalgic Neg.
 * to the pack's `provia conversion luts/` sRGB file specifically (per ADR 0004) — not the
 * linear-input `cube lut/` file of the same name — and Classic Neg. (Pixel) to FujiVibe's own
 * derived `derived-luts/` file (per ADR 0005/0006).
 *
 * `raw-assets/` is gitignored (ADR 0003's licensing constraint), so its files won't exist on a
 * fresh clone until staged locally per the ticket. These tests skip rather than fail when the
 * resources aren't on the classpath, instead of hard-failing the whole suite.
 */
class ResourceLutLoaderWiringTest {

    @BeforeEach
    fun assumeRealLutFilesAreStaged() {
        val classLoader = javaClass.classLoader
        assumeTrue(
            classLoader.getResource(FilmSimulation.NOSTALGIC_NEG.cubeResourceName) != null &&
                classLoader.getResource(FilmSimulation.CLASSIC_NEG_PIXEL.cubeResourceName) != null &&
                classLoader.getResource(FilmSimulation.NOSTALGIC_NEG_PIXEL.cubeResourceName) != null,
            "raw-assets/ and derived-luts/ .cube files aren't staged locally (see ADR 0003/0005/0007) — skipping",
        )
    }

    @Test
    fun `both registered Film Simulations load as real 32-point LUTs`() {
        val loader = ResourceLutLoader()

        val classicNegPixel = loader.load(FilmSimulation.CLASSIC_NEG_PIXEL)
        val nostalgicNeg = loader.load(FilmSimulation.NOSTALGIC_NEG)

        assertEquals(32, classicNegPixel.size)
        assertEquals(32, nostalgicNeg.size)
    }

    @Test
    fun `Nostalgic Neg samples the pack's known black-point and white-point grid values`() {
        val nostalgicNeg = ResourceLutLoader().load(FilmSimulation.NOSTALGIC_NEG)

        // First and last data lines of "Provia to Nostalgic Neg sRGB.cube", read directly off disk.
        assertRgbEquals(Rgb(0.01590f, 0.01602f, 0.01456f), nostalgicNeg.sample(0f, 0f, 0f))
        assertRgbEquals(Rgb(0.99184f, 0.98349f, 0.97731f), nostalgicNeg.sample(1f, 1f, 1f))
    }

    @Test
    fun `Classic Neg Pixel and Nostalgic Neg are distinct LUTs`() {
        val loader = ResourceLutLoader()

        val classicNegPixel = loader.load(FilmSimulation.CLASSIC_NEG_PIXEL)
        val nostalgicNeg = loader.load(FilmSimulation.NOSTALGIC_NEG)

        // Same sample point, two different real files: results must differ, otherwise both
        // simulations are silently loading the same underlying LUT.
        assertNotEquals(classicNegPixel.sample(0.5f, 0.2f, 0.8f), nostalgicNeg.sample(0.5f, 0.2f, 0.8f))
    }

    @Test
    fun `Nostalgic Neg Pixel loads as a real 32-point LUT`() {
        val loaded = ResourceLutLoader().load(FilmSimulation.NOSTALGIC_NEG_PIXEL)

        assertEquals(32, loaded.size)
    }

    @Test
    fun `Nostalgic Neg Pixel and plain Nostalgic Neg are distinct LUTs`() {
        val loader = ResourceLutLoader()

        val nostalgicNegPixel = loader.load(FilmSimulation.NOSTALGIC_NEG_PIXEL)
        val nostalgicNeg = loader.load(FilmSimulation.NOSTALGIC_NEG)

        assertNotEquals(nostalgicNegPixel.sample(0.5f, 0.2f, 0.8f), nostalgicNeg.sample(0.5f, 0.2f, 0.8f))
    }

    private fun assertRgbEquals(expected: Rgb, actual: Rgb, delta: Float = 1e-4f) {
        assertEquals(expected.r, actual.r, delta)
        assertEquals(expected.g, actual.g, delta)
        assertEquals(expected.b, actual.b, delta)
    }
}
