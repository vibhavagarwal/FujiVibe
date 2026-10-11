package com.fujivibe.render.tools

import com.fujivibe.render.Rgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CharcoalCorrectionTest {

    private fun luma(rgb: Rgb) = 0.2126f * rgb.r + 0.7152f * rgb.g + 0.0722f * rgb.b

    @Test
    fun `mono weights sum to one so neutral grays keep their level`() {
        val sum = CharcoalCorrection.MONO_RED_WEIGHT + CharcoalCorrection.MONO_GREEN_WEIGHT +
            CharcoalCorrection.MONO_BLUE_WEIGHT
        assertEquals(1f, sum, 1e-6f)
        assertEquals(0.5f, CharcoalCorrection.mono(Rgb(0.5f, 0.5f, 0.5f)), 1e-6f)
    }

    @Test
    fun `white balance pulls red and blue, leaving green`() {
        val result = CharcoalCorrection.whiteBalance(Rgb(0.5f, 0.5f, 0.5f))
        assertTrue(result.r < 0.5f)
        assertEquals(0.5f, result.g, 1e-6f)
        assertTrue(result.b < 0.5f)
    }

    @Test
    fun `tone curve keeps both endpoints fixed`() {
        assertEquals(0f, CharcoalCorrection.toneCurve(0f), 1e-6f)
        assertEquals(1f, CharcoalCorrection.toneCurve(1f), 1e-6f)
    }

    @Test
    fun `tone curve is an S, deepening shadows and brightening highlights`() {
        assertTrue(CharcoalCorrection.toneCurve(0.25f) < 0.25f)
        assertTrue(CharcoalCorrection.toneCurve(0.75f) > 0.75f)
        assertEquals(0.5f, CharcoalCorrection.toneCurve(0.5f), 1e-4f)
    }

    @Test
    fun `tone curve is monotonic`() {
        val outputs = (0..100).map { CharcoalCorrection.toneCurve(it / 100f) }
        outputs.zipWithNext().forEach { (a, b) -> assertTrue(b >= a, "curve decreased from $a to $b") }
    }

    @Test
    fun `the result is always a neutral gray`() {
        val inputs = listOf(
            Rgb(0.9f, 0.2f, 0.1f),
            Rgb(0.1f, 0.6f, 0.9f),
            Rgb(0f, 0f, 0f),
            Rgb(1f, 1f, 1f),
            Rgb(0.3f, 0.8f, 0.4f),
        )
        for (input in inputs) {
            val result = CharcoalCorrection.correct(input)
            assertEquals(result.r, result.g, 1e-6f)
            assertEquals(result.g, result.b, 1e-6f)
        }
    }

    @Test
    fun `yellow filter renders blue darker than red of equal brightness`() {
        val red = CharcoalCorrection.correct(Rgb(0.6f, 0f, 0f))
        val blue = CharcoalCorrection.correct(Rgb(0f, 0f, 0.6f))
        assertTrue(blue.r < red.r, "blue (${blue.r}) should render darker than red (${red.r})")
    }

    @Test
    fun `a blue sky renders darker than a neutral gray of the same luma`() {
        val sky = Rgb(0.45f, 0.70f, 0.95f)
        val gray = luma(sky)
        assertTrue(
            CharcoalCorrection.correct(sky).r < CharcoalCorrection.correct(Rgb(gray, gray, gray)).r,
            "sky should be darker than the gray it would otherwise convert to",
        )
    }

    @Test
    fun `black stays black and white stays white`() {
        assertEquals(0f, CharcoalCorrection.correct(Rgb(0f, 0f, 0f)).r, 1e-6f)
        // White balance trims red and blue slightly, so white lands just under 1 before the curve
        // pushes it back up; it must still read as essentially white.
        assertTrue(CharcoalCorrection.correct(Rgb(1f, 1f, 1f)).r > 0.97f)
    }
}
