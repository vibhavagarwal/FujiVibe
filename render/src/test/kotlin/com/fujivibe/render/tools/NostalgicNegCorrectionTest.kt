package com.fujivibe.render.tools

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
}
