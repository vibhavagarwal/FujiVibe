package com.fujivibe.render.tools

import com.fujivibe.render.Rgb
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LutCorrectionTest {

    @Test
    fun `tone curve lifts true black to the configured black point`() {
        val result = LutCorrection.toneCurve(0f)

        assertEquals(LutCorrection.BLACK_POINT_LIFT, result, 1e-4f)
    }

    @Test
    fun `tone curve maps full white to full white`() {
        val result = LutCorrection.toneCurve(1f)

        assertEquals(1f, result, 1e-4f)
    }

    @Test
    fun `tone curve is monotonically increasing`() {
        val samples = (0..20).map { it / 20f }

        val outputs = samples.map { LutCorrection.toneCurve(it) }

        for (i in 1 until outputs.size) {
            assertTrue(
                outputs[i] >= outputs[i - 1],
                "toneCurve(${samples[i]}) = ${outputs[i]} is less than " +
                    "toneCurve(${samples[i - 1]}) = ${outputs[i - 1]}",
            )
        }
    }

    @Test
    fun `tone curve pulls shadows below a plain black-point lift, not just a linear rescale`() {
        val shadowInput = 0.15f
        val plainLift = LutCorrection.BLACK_POINT_LIFT + (1f - LutCorrection.BLACK_POINT_LIFT) * shadowInput

        val result = LutCorrection.toneCurve(shadowInput)

        assertTrue(
            result < plainLift,
            "toneCurve($shadowInput) = $result should be pulled below the plain lift $plainLift",
        )
    }

    @Test
    fun `split-tone pushes pure black toward the cyan-green shadow offset`() {
        val result = LutCorrection.splitTone(Rgb(0f, 0f, 0f))

        assertEquals(LutCorrection.SHADOW_TINT.r, result.r, 1e-4f)
        assertEquals(LutCorrection.SHADOW_TINT.g, result.g, 1e-4f)
        assertEquals(LutCorrection.SHADOW_TINT.b, result.b, 1e-4f)
    }

    @Test
    fun `split-tone pushes pure white toward the highlight offset`() {
        val result = LutCorrection.splitTone(Rgb(1f, 1f, 1f))

        assertEquals((1f + LutCorrection.HIGHLIGHT_TINT.r).coerceIn(0f, 1f), result.r, 1e-4f)
        assertEquals((1f + LutCorrection.HIGHLIGHT_TINT.g).coerceIn(0f, 1f), result.g, 1e-4f)
        assertEquals((1f + LutCorrection.HIGHLIGHT_TINT.b).coerceIn(0f, 1f), result.b, 1e-4f)
    }

    @Test
    fun `split-tone never pushes a component outside the 0 to 1 range`() {
        val result = LutCorrection.splitTone(Rgb(1f, 1f, 1f))

        assertTrue(result.r in 0f..1f, "r = ${result.r} is outside [0, 1]")
        assertTrue(result.g in 0f..1f, "g = ${result.g} is outside [0, 1]")
        assertTrue(result.b in 0f..1f, "b = ${result.b} is outside [0, 1]")
    }

    @Test
    fun `split-tone's cool cast reaches further into midtones than a flat linear blend would`() {
        val midGray = Rgb(0.5f, 0.5f, 0.5f)
        val flatLinearBlendGreenOffset = LutCorrection.SHADOW_TINT.g * 0.5f + LutCorrection.HIGHLIGHT_TINT.g * 0.5f

        val result = LutCorrection.splitTone(midGray)

        val actualGreenOffset = result.g - midGray.g
        assertTrue(
            actualGreenOffset > flatLinearBlendGreenOffset,
            "expected more shadow-tint influence at midtones than a flat 50/50 blend " +
                "($flatLinearBlendGreenOffset), got $actualGreenOffset",
        )
    }

    @Test
    fun `rgb to hsl and back round-trips pure red`() {
        val original = Rgb(1f, 0f, 0f)

        val roundTripped = LutCorrection.hslToRgb(LutCorrection.rgbToHsl(original))

        assertEquals(original.r, roundTripped.r, 1e-4f)
        assertEquals(original.g, roundTripped.g, 1e-4f)
        assertEquals(original.b, roundTripped.b, 1e-4f)
    }

    @Test
    fun `rgb to hsl and back round-trips a mid gray`() {
        val original = Rgb(0.5f, 0.5f, 0.5f)

        val roundTripped = LutCorrection.hslToRgb(LutCorrection.rgbToHsl(original))

        assertEquals(original.r, roundTripped.r, 1e-4f)
        assertEquals(original.g, roundTripped.g, 1e-4f)
        assertEquals(original.b, roundTripped.b, 1e-4f)
    }

    @Test
    fun `pure red hue is 0 degrees`() {
        val hsl = LutCorrection.rgbToHsl(Rgb(1f, 0f, 0f))

        assertEquals(0f, hsl.hueDegrees, 1e-3f)
    }

    @Test
    fun `hsl shift rotates a red hue toward magenta`() {
        val hsl = LutCorrection.rgbToHsl(LutCorrection.hslShift(Rgb(0.7f, 0.3f, 0.3f)))

        val expectedHue = (LutCorrection.REDS_TARGET_HUE + LutCorrection.REDS_HUE_SHIFT_DEG).mod(360f)
        assertEquals(expectedHue, hsl.hueDegrees, 0.5f)
    }

    @Test
    fun `hsl shift retains more of a red's saturation than the flat global vibrance cut alone would, and lowers its lightness`() {
        val original = Rgb(0.7f, 0.3f, 0.3f)
        val originalHsl = LutCorrection.rgbToHsl(original)
        val saturationWithNoLocalBoost = originalHsl.saturation * (1f - LutCorrection.GLOBAL_VIBRANCE_REDUCTION)

        val shiftedHsl = LutCorrection.rgbToHsl(LutCorrection.hslShift(original))

        assertTrue(
            shiftedHsl.saturation > saturationWithNoLocalBoost,
            "expected saturation above the flat-cut baseline $saturationWithNoLocalBoost, got ${shiftedHsl.saturation}",
        )
        assertTrue(
            shiftedHsl.lightness < originalHsl.lightness,
            "expected lightness to fall below ${originalHsl.lightness}, got ${shiftedHsl.lightness}",
        )
    }

    @Test
    fun `hsl shift rotates pure blue toward teal`() {
        val hsl = LutCorrection.rgbToHsl(LutCorrection.hslShift(Rgb(0f, 0f, 1f)))

        val expectedHue = (LutCorrection.BLUES_TARGET_HUE + LutCorrection.BLUES_HUE_SHIFT_DEG).mod(360f)
        assertEquals(expectedHue, hsl.hueDegrees, 0.5f)
    }

    @Test
    fun `hsl shift applies an additional saturation cut to yellows and oranges, on top of global vibrance`() {
        // Hue 45 degrees sits outside both the reds (target 0, width 40) and blues (target 240,
        // width 45) influence, so only the yellow/orange band and the global cut apply here.
        val yellowOrange = LutCorrection.hslToRgb(LutCorrection.Hsl(hueDegrees = 45f, saturation = 0.5f, lightness = 0.5f))
        val originalHsl = LutCorrection.rgbToHsl(yellowOrange)

        val shiftedHsl = LutCorrection.rgbToHsl(LutCorrection.hslShift(yellowOrange))

        val expectedSaturation = originalHsl.saturation *
            (1f - LutCorrection.YELLOW_ORANGE_SAT_REDUCTION) *
            (1f - LutCorrection.GLOBAL_VIBRANCE_REDUCTION)
        assertEquals(expectedSaturation, shiftedHsl.saturation, 1e-3f)
    }

    @Test
    fun `hsl shift leaves an unrelated hue's angle alone but still reduces its vibrance`() {
        val green = Rgb(0f, 1f, 0f)
        val originalHsl = LutCorrection.rgbToHsl(green)

        val shiftedHsl = LutCorrection.rgbToHsl(LutCorrection.hslShift(green))

        assertEquals(originalHsl.hueDegrees, shiftedHsl.hueDegrees, 0.5f)
        assertEquals(
            originalHsl.saturation * (1f - LutCorrection.GLOBAL_VIBRANCE_REDUCTION),
            shiftedHsl.saturation,
            1e-3f,
        )
    }

    @Test
    fun `an out-of-range split-tone result does not erase the highlight tint into flat white`() {
        // A near-white value whose split-tone highlight offset would overflow past 1.0 on some
        // channel if splitTone didn't clamp its own output (the bug: an unclamped >1 channel fed
        // into rgbToHsl drives saturation negative, which coerceIn then flattens to pure white,
        // silently erasing the tint split-tone was supposed to add).
        val nearWhite = Rgb(0.999f, 0.999f, 0.999f)

        val result = LutCorrection.hslShift(LutCorrection.splitTone(nearWhite))

        assertTrue(
            result.r != result.g || result.g != result.b,
            "expected the highlight tint to survive, but got a flat neutral $result",
        )
    }

    @Test
    fun `correct applies tone curve, then split-tone, then hsl shift, in that order`() {
        val input = Rgb(0.6f, 0.2f, 0.15f)
        val expected = LutCorrection.hslShift(
            LutCorrection.splitTone(
                Rgb(
                    LutCorrection.toneCurve(input.r),
                    LutCorrection.toneCurve(input.g),
                    LutCorrection.toneCurve(input.b),
                ),
            ),
        )

        val result = LutCorrection.correct(input)

        assertEquals(expected.r, result.r, 1e-5f)
        assertEquals(expected.g, result.g, 1e-5f)
        assertEquals(expected.b, result.b, 1e-5f)
    }
}
