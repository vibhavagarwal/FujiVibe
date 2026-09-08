package com.fujivibe.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verifies the Film Simulation registry is wired to the pack's `provia conversion luts/`
 * sRGB files specifically (per ADR 0004) — not the linear-input `cube lut/` files of the
 * same name. The module's test resources are configured to pull in only the two sRGB
 * files from that folder (see render/build.gradle.kts), so a successful, correctly-sized
 * parse here is only possible if the wiring points at the right files.
 *
 * `raw-assets/` is gitignored (ADR 0003's licensing constraint), so these files won't exist
 * on a fresh clone until staged locally per the ticket. These tests skip rather than fail
 * when the resources aren't on the classpath, instead of hard-failing the whole suite.
 */
class ResourceLutLoaderWiringTest {

    @BeforeEach
    fun assumeRealLutFilesAreStaged() {
        val classLoader = javaClass.classLoader
        assumeTrue(
            classLoader.getResource(FilmSimulation.CLASSIC_NEG.cubeResourceName) != null &&
                classLoader.getResource(FilmSimulation.NOSTALGIC_NEG.cubeResourceName) != null,
            "raw-assets/ .cube files aren't staged locally (see ADR 0003) — skipping",
        )
    }

    @Test
    fun `both launch Film Simulations load as real 32-point LUTs`() {
        val loader = ResourceLutLoader()

        val classicNeg = loader.load(FilmSimulation.CLASSIC_NEG)
        val nostalgicNeg = loader.load(FilmSimulation.NOSTALGIC_NEG)

        assertEquals(32, classicNeg.size)
        assertEquals(32, nostalgicNeg.size)
    }

    @Test
    fun `Classic Neg samples the pack's known black-point and white-point grid values`() {
        val classicNeg = ResourceLutLoader().load(FilmSimulation.CLASSIC_NEG)

        // First and last data lines of "Provia to Classic Neg sRGB.cube", read directly off disk.
        assertRgbEquals(Rgb(0.00923f, 0.01141f, 0.00931f), classicNeg.sample(0f, 0f, 0f))
        assertRgbEquals(Rgb(0.99322f, 0.98923f, 0.98906f), classicNeg.sample(1f, 1f, 1f))
    }

    @Test
    fun `Nostalgic Neg samples the pack's known black-point and white-point grid values`() {
        val nostalgicNeg = ResourceLutLoader().load(FilmSimulation.NOSTALGIC_NEG)

        // First and last data lines of "Provia to Nostalgic Neg sRGB.cube", read directly off disk.
        assertRgbEquals(Rgb(0.01590f, 0.01602f, 0.01456f), nostalgicNeg.sample(0f, 0f, 0f))
        assertRgbEquals(Rgb(0.99184f, 0.98349f, 0.97731f), nostalgicNeg.sample(1f, 1f, 1f))
    }

    @Test
    fun `Classic Neg interpolates between its real grid points, not just at exact corners`() {
        val classicNeg = ResourceLutLoader().load(FilmSimulation.CLASSIC_NEG)

        // Data lines for r=3 and r=4 at g=0, b=0 (4th and 5th lines of
        // "Provia to Classic Neg sRGB.cube", read directly off disk):
        //   r=3: 0.06269 0.00986 0.00000
        //   r=4: 0.08403 0.01063 0.00000
        // Sampling at r = 3.5/31 lands exactly halfway between them (rt=0.5),
        // with g and b held at exact grid point 0 (gt=bt=0), so the expected
        // result is a plain midpoint of the two real rows above.
        val result = classicNeg.sample(r = 3.5f / 31f, g = 0f, b = 0f)

        assertRgbEquals(Rgb(0.07336f, 0.010245f, 0.00000f), result)
    }

    @Test
    fun `Classic Neg and Nostalgic Neg are distinct LUTs`() {
        val loader = ResourceLutLoader()

        val classicNeg = loader.load(FilmSimulation.CLASSIC_NEG)
        val nostalgicNeg = loader.load(FilmSimulation.NOSTALGIC_NEG)

        // Same black-point sample point, two different real files: results must differ,
        // otherwise both simulations are silently loading the same underlying LUT.
        assertNotEquals(classicNeg.sample(0.5f, 0.2f, 0.8f), nostalgicNeg.sample(0.5f, 0.2f, 0.8f))
    }

    private fun assertRgbEquals(expected: Rgb, actual: Rgb, delta: Float = 1e-4f) {
        assertEquals(expected.r, actual.r, delta)
        assertEquals(expected.g, actual.g, delta)
        assertEquals(expected.b, actual.b, delta)
    }
}
