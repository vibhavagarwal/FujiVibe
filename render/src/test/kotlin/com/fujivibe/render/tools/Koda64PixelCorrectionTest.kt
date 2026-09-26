package com.fujivibe.render.tools

import com.fujivibe.render.Rgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Koda64PixelCorrectionTest {

    private fun luma(c: Rgb) = 0.2126f * c.r + 0.7152f * c.g + 0.0722f * c.b

    private fun hsvSaturation(c: Rgb): Float {
        val max = maxOf(c.r, c.g, c.b)
        return if (max == 0f) 0f else (max - minOf(c.r, c.g, c.b)) / max
    }

    @Test
    fun `white balance warms a neutral gray, red above green above blue`() {
        val result = Koda64PixelCorrection.whiteBalance(Rgb(0.5f, 0.5f, 0.5f))

        assertTrue(result.r > result.g && result.g > result.b, "expected warm ordering, got $result")
    }

    @Test
    fun `tone curve keeps black at black`() {
        assertEquals(0f, Koda64PixelCorrection.toneCurve(0f), 1e-6f)
    }

    @Test
    fun `tone curve deepens shadows below the identity line`() {
        assertTrue(Koda64PixelCorrection.toneCurve(0.25f) < 0.25f)
    }

    @Test
    fun `tone curve rolls the white point off to just under 1`() {
        val white = Koda64PixelCorrection.toneCurve(1f)

        assertTrue(white < 1f, "white point $white should be pulled below 1")
        assertTrue(white > 0.9f, "white point $white should stay close to 1, not crushed")
    }

    @Test
    fun `tone curve is monotonically increasing`() {
        val outputs = (0..40).map { Koda64PixelCorrection.toneCurve(it / 40f) }

        for (i in 1 until outputs.size) {
            assertTrue(outputs[i] >= outputs[i - 1], "not monotonic at step $i: $outputs")
        }
    }

    @Test
    fun `color boosts saturation of a colorful pixel`() {
        val input = Rgb(0.8f, 0.4f, 0.3f)

        assertTrue(hsvSaturation(Koda64PixelCorrection.color(input)) > hsvSaturation(input))
    }

    @Test
    fun `color leaves a neutral gray neutral`() {
        val result = Koda64PixelCorrection.color(Rgb(0.5f, 0.5f, 0.5f))

        assertEquals(result.r, result.g, 1e-5f)
        assertEquals(result.g, result.b, 1e-5f)
    }

    @Test
    fun `color chrome darkens a strongly saturated pixel`() {
        val input = Rgb(0.9f, 0.2f, 0.2f)

        assertTrue(luma(Koda64PixelCorrection.color(input)) < luma(input))
    }

    @Test
    fun `color deepens a pale teal sky toward blue by pulling green back`() {
        val sky = Rgb(0.55f, 0.85f, 0.85f)

        val result = Koda64PixelCorrection.color(sky)

        // Relative to blue, green must drop: the pixel moves from teal toward blue.
        assertTrue(result.g / result.b < sky.g / sky.b, "expected bluer than $sky, got $result")
    }

    @Test
    fun `color leaves a green-leaning teal like foliage or sea-green water alone`() {
        val foliage = Rgb(0.30f, 0.60f, 0.40f)
        val withoutBlueStep = 0.2126f * foliage.r + 0.7152f * foliage.g + 0.0722f * foliage.b

        val result = Koda64PixelCorrection.color(foliage)

        // Green stays dominant and un-pulled: same as the plain saturation boost would give.
        val boostedG = withoutBlueStep + (foliage.g - withoutBlueStep) * (1f + Koda64PixelCorrection.COLOR_BOOST)
        val boostedR = withoutBlueStep + (foliage.r - withoutBlueStep) * (1f + Koda64PixelCorrection.COLOR_BOOST)
        val boostedB = withoutBlueStep + (foliage.b - withoutBlueStep) * (1f + Koda64PixelCorrection.COLOR_BOOST)
        val saturation = (maxOf(boostedR, boostedG, boostedB) - minOf(boostedR, boostedG, boostedB)) /
            maxOf(boostedR, boostedG, boostedB)
        val density = 1f - Koda64PixelCorrection.COLOR_CHROME_DARKEN * saturation
        assertEquals(boostedG * density, result.g, 1e-4f)
    }

    @Test
    fun `skin weight is high for a typical skin tone and zero for sky, foliage and gray`() {
        assertTrue(Koda64PixelCorrection.skinWeight(Rgb(0.80f, 0.60f, 0.50f)) > 0.8f)
        assertEquals(0f, Koda64PixelCorrection.skinWeight(Rgb(0.45f, 0.70f, 0.95f)), 1e-6f)
        assertEquals(0f, Koda64PixelCorrection.skinWeight(Rgb(0.30f, 0.60f, 0.40f)), 1e-6f)
        assertEquals(0f, Koda64PixelCorrection.skinWeight(Rgb(0.50f, 0.50f, 0.50f)), 1e-6f)
    }

    @Test
    fun `lightenSkin pulls skin partway back toward the input, making it lighter`() {
        val input = Rgb(0.80f, 0.60f, 0.50f)
        val corrected = Koda64PixelCorrection.correct(input)

        val result = Koda64PixelCorrection.lightenSkin(input, corrected)

        assertTrue(luma(result) > luma(corrected), "skin should be lighter than the pure Kodachrome result")
        assertTrue(luma(result) < luma(input) + 1e-4f, "but not lighter than the untouched input")
    }

    @Test
    fun `lightenSkin leaves non-skin pixels exactly as corrected`() {
        val sky = Rgb(0.45f, 0.70f, 0.95f)
        val corrected = Koda64PixelCorrection.correct(sky)

        assertEquals(corrected, Koda64PixelCorrection.lightenSkin(sky, corrected))
    }

    @Test
    fun `correct keeps every output channel in range and warms mid gray`() {
        val result = Koda64PixelCorrection.correct(Rgb(0.5f, 0.5f, 0.5f))

        assertTrue(result.r in 0f..1f && result.g in 0f..1f && result.b in 0f..1f)
        assertTrue(result.r > result.b, "mid gray should come out warm, got $result")
    }
}
