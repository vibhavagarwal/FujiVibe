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

    @Test
    fun `rgb to hsl and back round-trips a mid gray`() {
        val original = Rgb(0.5f, 0.5f, 0.5f)

        val roundTripped = NostalgicNegCorrection.hslToRgb(NostalgicNegCorrection.rgbToHsl(original))

        assertEquals(original.r, roundTripped.r, 1e-4f)
        assertEquals(original.g, roundTripped.g, 1e-4f)
        assertEquals(original.b, roundTripped.b, 1e-4f)
    }

    @Test
    fun `rgb to hsl and back round-trips pure green`() {
        val original = Rgb(0f, 1f, 0f)

        val roundTripped = NostalgicNegCorrection.hslToRgb(NostalgicNegCorrection.rgbToHsl(original))

        assertEquals(original.r, roundTripped.r, 1e-4f)
        assertEquals(original.g, roundTripped.g, 1e-4f)
        assertEquals(original.b, roundTripped.b, 1e-4f)
    }

    @Test
    fun `hsl shift desaturates a green hue`() {
        val green = Rgb(0.2f, 0.6f, 0.2f) // hue 120 degrees
        val originalHsl = NostalgicNegCorrection.rgbToHsl(green)

        val shiftedHsl = NostalgicNegCorrection.rgbToHsl(NostalgicNegCorrection.hslShift(green))

        assertTrue(
            shiftedHsl.saturation < originalHsl.saturation,
            "expected saturation below ${originalHsl.saturation}, got ${shiftedHsl.saturation}",
        )
    }

    @Test
    fun `hsl shift desaturates a blue hue`() {
        val blue = Rgb(0.2f, 0.2f, 0.6f) // hue 240 degrees
        val originalHsl = NostalgicNegCorrection.rgbToHsl(blue)

        val shiftedHsl = NostalgicNegCorrection.rgbToHsl(NostalgicNegCorrection.hslShift(blue))

        assertTrue(
            shiftedHsl.saturation < originalHsl.saturation,
            "expected saturation below ${originalHsl.saturation}, got ${shiftedHsl.saturation}",
        )
    }

    @Test
    fun `hsl shift boosts saturation for a red-orange hue`() {
        val red = Rgb(0.6f, 0.2f, 0.2f) // hue 0 degrees
        val originalHsl = NostalgicNegCorrection.rgbToHsl(red)

        val shiftedHsl = NostalgicNegCorrection.rgbToHsl(NostalgicNegCorrection.hslShift(red))

        assertTrue(
            shiftedHsl.saturation > originalHsl.saturation,
            "expected saturation above ${originalHsl.saturation}, got ${shiftedHsl.saturation}",
        )
    }

    @Test
    fun `hsl shift leaves an unrelated hue's saturation alone`() {
        val yellow = Rgb(0.6f, 0.6f, 0.2f) // hue 60 degrees, outside all targeted bands
        val originalHsl = NostalgicNegCorrection.rgbToHsl(yellow)

        val shiftedHsl = NostalgicNegCorrection.rgbToHsl(NostalgicNegCorrection.hslShift(yellow))

        assertEquals(originalHsl.saturation, shiftedHsl.saturation, 1e-4f)
    }

    @Test
    fun `hsl shift never produces saturation outside 0 to 1`() {
        val vividRed = Rgb(1f, 0f, 0f)

        val shiftedHsl = NostalgicNegCorrection.rgbToHsl(NostalgicNegCorrection.hslShift(vividRed))

        assertTrue(shiftedHsl.saturation in 0f..1f, "saturation ${shiftedHsl.saturation} outside [0, 1]")
    }

    @Test
    fun `correct produces the expected warm, milky near-black for the source LUT's darkest grid entry`() {
        // Input is "Provia to Nostalgic Neg sRGB.cube"'s actual first grid entry (r=g=b=0 corner).
        // Expected output independently verified by hand against the committed constants during
        // final review — also matches derived-luts/Nostalgic Neg Pixel sRGB.cube's first data row
        // exactly, confirming the committed .cube is in sync with these constants.
        val input = Rgb(0.0159f, 0.01602f, 0.01456f)

        val result = NostalgicNegCorrection.correct(input)

        assertEquals(0.13351095f, result.r, 1e-4f)
        assertEquals(0.09231381f, result.g, 1e-4f)
        assertEquals(0.050178207f, result.b, 1e-4f)
    }

    @Test
    fun `correct applies tone curve, then split-tone, then hsl shift, in that order`() {
        val input = Rgb(0.6f, 0.2f, 0.15f)
        val expected = NostalgicNegCorrection.hslShift(
            NostalgicNegCorrection.splitTone(
                Rgb(
                    NostalgicNegCorrection.toneCurve(input.r),
                    NostalgicNegCorrection.toneCurve(input.g),
                    NostalgicNegCorrection.toneCurve(input.b),
                ),
            ),
        )

        val result = NostalgicNegCorrection.correct(input)

        assertEquals(expected.r, result.r, 1e-5f)
        assertEquals(expected.g, result.g, 1e-5f)
        assertEquals(expected.b, result.b, 1e-5f)
    }
}
