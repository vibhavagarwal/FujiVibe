package com.fujivibe.recipe

import com.fujivibe.camera.FujiFilmSim
import com.fujivibe.camera.RawConvProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RecipeToProfileTest {

    /** A 29-parameter block whose every parameter starts at 1000 plus its index, so a change stands out. */
    private fun profile(): RawConvProfile {
        val count = 29
        val b = ByteBuffer.allocate(20 + count * 4).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(count.toShort())
        b.position(20)
        repeat(count) { b.putInt(1000 + it) }
        return RawConvProfile(b.array())
    }

    private val kodachrome = Recipe(
        id = "k", name = "K", kind = RecipeKind.COMMUNITY, baseSimulation = FujiFilmSim.CLASSIC_CHROME,
        settings = RecipeSettings(
            dynamicRange = DynamicRange.DR200,
            grain = Grain(GrainStrength.WEAK, GrainSize.SMALL),
            colorChrome = Level.STRONG,
            colorChromeFxBlue = Level.OFF,
            whiteBalance = WhiteBalance(WhiteBalanceMode.DAYLIGHT, shiftR = 2, shiftB = -5),
            highlight = 0.0, shadow = 0.5, color = 2.0, sharpness = 1.0, highIsoNr = -4, clarity = 3.0,
        ),
    )

    @Test
    fun `a recipe writes each of its settings in the camera's encoding`() {
        val p = kodachrome.applyTo(profile())

        assertEquals(0x0B, p.field(RawConvProfile.FILM_SIMULATION))
        assertEquals(200, p.field(RawConvProfile.DYNAMIC_RANGE)) // asked 200; the block's own value is far above
        assertEquals(2, p.field(RawConvProfile.GRAIN)) // weak, small
        assertEquals(3, p.field(RawConvProfile.COLOR_CHROME)) // strong
        assertEquals(1, p.field(RawConvProfile.COLOR_CHROME_FX_BLUE)) // off
        assertEquals(0x0004, p.field(RawConvProfile.WHITE_BALANCE)) // daylight
        assertEquals(2, p.field(RawConvProfile.WB_SHIFT_R))
        assertEquals(-5, p.field(RawConvProfile.WB_SHIFT_B))
        assertEquals(0, p.field(RawConvProfile.HIGHLIGHT_TONE))
        assertEquals(5, p.field(RawConvProfile.SHADOW_TONE)) // +0.5 is stored times ten
        assertEquals(20, p.field(RawConvProfile.COLOR))
        assertEquals(10, p.field(RawConvProfile.SHARPNESS))
        assertEquals(30, p.field(RawConvProfile.CLARITY))
    }

    @Test
    fun `a recipe never asks for more Dynamic Range than the RAF was shot with`() {
        val shotAt200 = profile().with(RawConvProfile.DYNAMIC_RANGE, 200)
        val wants400 = kodachrome.copy(settings = RecipeSettings(dynamicRange = DynamicRange.DR400))

        assertEquals(200, wants400.applyTo(shotAt200).field(RawConvProfile.DYNAMIC_RANGE))
        assertEquals("Dynamic range DR400 held to DR200, as the RAF was shot", wants400.dynamicRangeLimit(shotAt200))
    }

    @Test
    fun `less Dynamic Range than the RAF was shot with is allowed, and needs no note`() {
        val shotAt400 = profile().with(RawConvProfile.DYNAMIC_RANGE, 400)
        val wants100 = kodachrome.copy(settings = RecipeSettings(dynamicRange = DynamicRange.DR100))

        assertEquals(100, wants100.applyTo(shotAt400).field(RawConvProfile.DYNAMIC_RANGE))
        assertEquals(null, wants100.dynamicRangeLimit(shotAt400))
    }

    @Test
    fun `a setting the recipe does not have is left as the RAF recorded it`() {
        val bare = kodachrome.copy(settings = RecipeSettings())

        val p = bare.applyTo(profile())

        assertEquals(0x0B, p.field(RawConvProfile.FILM_SIMULATION))
        assertEquals(1000 + RawConvProfile.GRAIN, p.field(RawConvProfile.GRAIN))
        assertEquals(1000 + RawConvProfile.HIGHLIGHT_TONE, p.field(RawConvProfile.HIGHLIGHT_TONE))
        assertEquals(1000 + RawConvProfile.WHITE_BALANCE, p.field(RawConvProfile.WHITE_BALANCE))
    }

    @Test
    fun `grain codes are one flat list`() {
        fun grain(g: Grain) = kodachrome.copy(settings = RecipeSettings(grain = g)).applyTo(profile()).field(RawConvProfile.GRAIN)

        assertEquals(1, grain(Grain(GrainStrength.OFF)))
        assertEquals(2, grain(Grain(GrainStrength.WEAK, GrainSize.SMALL)))
        assertEquals(3, grain(Grain(GrainStrength.STRONG, GrainSize.SMALL)))
        assertEquals(4, grain(Grain(GrainStrength.WEAK, GrainSize.LARGE)))
        assertEquals(5, grain(Grain(GrainStrength.STRONG, GrainSize.LARGE)))
    }

    @Test
    fun `a kelvin white balance writes the temperature as well as the mode`() {
        val r = kodachrome.copy(settings = RecipeSettings(whiteBalance = WhiteBalance(WhiteBalanceMode.KELVIN, kelvin = 5800, shiftR = 1, shiftB = -3)))

        val p = r.applyTo(profile())

        assertEquals(0x8007, p.field(RawConvProfile.WHITE_BALANCE))
        assertEquals(5800, p.field(RawConvProfile.WB_COLOR_TEMP))
    }

    @Test
    fun `D Range Priority and negative tone steps are written`() {
        val r = kodachrome.copy(settings = RecipeSettings(dRangePriority = DRangePriority.STRONG, highlight = -2.0, shadow = -1.5))

        val p = r.applyTo(profile())

        assertEquals(3, p.field(RawConvProfile.D_RANGE_PRIORITY))
        assertEquals(-20, p.field(RawConvProfile.HIGHLIGHT_TONE))
        assertEquals(-15, p.field(RawConvProfile.SHADOW_TONE))
    }

    @Test
    fun `settings that cannot be written yet are named`() {
        assertEquals(listOf("High ISO NR"), kodachrome.unsupportedSettings())
        val mono = kodachrome.copy(settings = RecipeSettings(monochromaticColor = MonochromaticColor(-1, 0), dynamicRange = DynamicRange.AUTO))
        assertTrue(mono.unsupportedSettings().containsAll(listOf("Monochromatic colour", "Dynamic range auto")))
    }
}
