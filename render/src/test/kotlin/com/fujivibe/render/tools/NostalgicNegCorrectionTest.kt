package com.fujivibe.render.tools

import com.fujivibe.render.Rgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NostalgicNegCorrectionTest {

    @Test
    fun `tone curve lifts true black to the configured black point`() {
        val result = NostalgicNegCorrection.toneCurve(0f)

        assertEquals(NostalgicNegCorrection.BLACK_POINT_LIFT, result, 1e-4f)
    }

    @Test
    fun `tone curve maps full white to full white`() {
        val result = NostalgicNegCorrection.toneCurve(1f)

        assertEquals(1f, result, 1e-4f)
    }

    @Test
    fun `tone curve is monotonically increasing`() {
        val samples = (0..20).map { it / 20f }

        val outputs = samples.map { NostalgicNegCorrection.toneCurve(it) }

        for (i in 1 until outputs.size) {
            assertTrue(
                outputs[i] >= outputs[i - 1],
                "toneCurve(${samples[i]}) = ${outputs[i]} is less than " +
                    "toneCurve(${samples[i - 1]}) = ${outputs[i - 1]}",
            )
        }
    }

    @Test
    fun `tone curve pulls highlights below a plain black-point lift, compressing the white point`() {
        // The brief calls for the white point to be "slightly lowered to compress the
        // highlights, preventing stark digital whites" — near-white input should read dimmer
        // than a plain lift, not pass through unchanged.
        val highlightInput = 0.95f
        val plainLift = NostalgicNegCorrection.BLACK_POINT_LIFT +
            (1f - NostalgicNegCorrection.BLACK_POINT_LIFT) * highlightInput

        val result = NostalgicNegCorrection.toneCurve(highlightInput)

        assertTrue(
            result < plainLift,
            "toneCurve($highlightInput) = $result should be pulled below the plain lift $plainLift",
        )
    }

    @Test
    fun `split-tone pushes pure black toward the warm shadow offset`() {
        val result = NostalgicNegCorrection.splitTone(Rgb(0f, 0f, 0f))

        assertEquals((0f + NostalgicNegCorrection.SHADOW_TINT.r).coerceIn(0f, 1f), result.r, 1e-4f)
        assertEquals((0f + NostalgicNegCorrection.SHADOW_TINT.g).coerceIn(0f, 1f), result.g, 1e-4f)
        assertEquals((0f + NostalgicNegCorrection.SHADOW_TINT.b).coerceIn(0f, 1f), result.b, 1e-4f)
    }

    @Test
    fun `split-tone pushes pure white toward the warm highlight offset`() {
        val result = NostalgicNegCorrection.splitTone(Rgb(1f, 1f, 1f))

        assertEquals((1f + NostalgicNegCorrection.HIGHLIGHT_TINT.r).coerceIn(0f, 1f), result.r, 1e-4f)
        assertEquals((1f + NostalgicNegCorrection.HIGHLIGHT_TINT.g).coerceIn(0f, 1f), result.g, 1e-4f)
        assertEquals((1f + NostalgicNegCorrection.HIGHLIGHT_TINT.b).coerceIn(0f, 1f), result.b, 1e-4f)
    }

    @Test
    fun `both shadow and highlight tints are warm (positive red, negative blue)`() {
        // Unlike Classic Neg (Pixel), where highlights fade toward a cool/neutral tint, the
        // brief calls for an amber push across the whole tonal range — both ends stay warm.
        assertTrue(NostalgicNegCorrection.SHADOW_TINT.r > 0f && NostalgicNegCorrection.SHADOW_TINT.b < 0f)
        assertTrue(NostalgicNegCorrection.HIGHLIGHT_TINT.r > 0f && NostalgicNegCorrection.HIGHLIGHT_TINT.b < 0f)
    }

    @Test
    fun `split-tone never pushes a component outside the 0 to 1 range`() {
        val result = NostalgicNegCorrection.splitTone(Rgb(1f, 1f, 1f))

        assertTrue(result.r in 0f..1f, "r = ${result.r} is outside [0, 1]")
        assertTrue(result.g in 0f..1f, "g = ${result.g} is outside [0, 1]")
        assertTrue(result.b in 0f..1f, "b = ${result.b} is outside [0, 1]")
    }
}
