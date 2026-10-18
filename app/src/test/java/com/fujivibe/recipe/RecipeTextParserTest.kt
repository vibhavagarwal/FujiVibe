package com.fujivibe.recipe

import com.fujivibe.camera.FujiFilmSim
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecipeTextParserTest {

    private val kodachrome = """
        Kodachrome 64
        Film Simulation: Classic Chrome
        Dynamic Range: DR200
        Grain Effect: Weak, Small
        Color Chrome Effect: Strong
        Color Chrome FX Blue: Off
        White Balance: Daylight, +2 Red & -5 Blue
        Highlight: 0
        Shadow: +0.5
        Color: +2
        Sharpness: +1
        High ISO NR: -4
        Clarity: +3
        ISO: Auto, up to ISO 6400
        Exposure Compensation: 0 to +2/3 (typically)
    """.trimIndent()

    @Test
    fun `a typical recipe list is read in full`() {
        val result = RecipeTextParser.parse(kodachrome)
        val r = result.recipe!!

        assertEquals(emptyList<String>(), result.warnings)
        assertEquals("Kodachrome 64", r.name)
        assertEquals("kodachrome-64", r.id)
        assertEquals(RecipeKind.OWN, r.kind)
        assertEquals(FujiFilmSim.CLASSIC_CHROME, r.baseSimulation)
        assertEquals(DynamicRange.DR200, r.settings.dynamicRange)
        assertEquals(Grain(GrainStrength.WEAK, GrainSize.SMALL), r.settings.grain)
        assertEquals(Level.STRONG, r.settings.colorChrome)
        assertEquals(Level.OFF, r.settings.colorChromeFxBlue)
        assertEquals(WhiteBalance(WhiteBalanceMode.DAYLIGHT, null, 2, -5), r.settings.whiteBalance)
        assertEquals(0.0, r.settings.highlight)
        assertEquals(0.5, r.settings.shadow)
        assertEquals(2.0, r.settings.color)
        assertEquals(1.0, r.settings.sharpness)
        assertEquals(-4, r.settings.highIsoNr)
        assertEquals(3.0, r.settings.clarity)
        assertEquals("Auto, up to ISO 6400", r.shootingNotes.iso)
    }

    @Test
    fun `several settings on one line are all read`() {
        val r = RecipeTextParser.parse("Film Simulation: Velvia\nHighlight: +1   Shadow: +2   Color: +3").recipe!!

        assertEquals(1.0, r.settings.highlight)
        assertEquals(2.0, r.settings.shadow)
        assertEquals(3.0, r.settings.color)
    }

    @Test
    fun `a name passed in wins over the first line`() {
        val r = RecipeTextParser.parse(kodachrome, nameHint = "My Slide").recipe!!

        assertEquals("My Slide", r.name)
    }

    @Test
    fun `white balance can be a temperature, or shifts written as R and B`() {
        val kelvin = RecipeTextParser.parse("Film Simulation: Classic Neg.\nWhite Balance: 5800K, +1 Red & -3 Blue").recipe!!
        assertEquals(WhiteBalance(WhiteBalanceMode.KELVIN, 5800, 1, -3), kelvin.settings.whiteBalance)

        val auto = RecipeTextParser.parse("Film Simulation: Classic Chrome\nWhite Balance: Auto, R +2 B -4").recipe!!
        assertEquals(WhiteBalance(WhiteBalanceMode.AUTO, null, 2, -4), auto.settings.whiteBalance)
    }

    @Test
    fun `black and white recipes with a filter and monochromatic colour are read`() {
        val r = RecipeTextParser.parse(
            "Film Simulation: Acros + R\nGrain Effect: Strong, Large\nMonochromatic Color: WC -1 / MG 0\nClarity: +3",
        ).recipe!!

        assertEquals(FujiFilmSim.ACROS_R, r.baseSimulation)
        assertEquals(Grain(GrainStrength.STRONG, GrainSize.LARGE), r.settings.grain)
        assertEquals(MonochromaticColor(-1, 0), r.settings.monochromaticColor)
    }

    @Test
    fun `D Range Priority and the minus sign used by some websites are read`() {
        val r = RecipeTextParser.parse("Film Simulation: Classic Chrome\nD Range Priority: Strong\nHighlight: −2").recipe!!

        assertEquals(DRangePriority.STRONG, r.settings.dRangePriority)
        assertEquals(-2.0, r.settings.highlight)
    }

    @Test
    fun `a value it cannot read is reported and left unset, not guessed`() {
        val result = RecipeTextParser.parse("Film Simulation: Provia\nGrain Effect: whatever\nSharpness: +1")

        assertNull(result.recipe!!.settings.grain)
        assertEquals(1.0, result.recipe!!.settings.sharpness)
        assertTrue(result.warnings.single().contains("Grain Effect"))
    }

    @Test
    fun `a line it does not understand is reported`() {
        val result = RecipeTextParser.parse("Film Simulation: Provia\nThis recipe was made on a rainy day in Oregon and is great for everything under the sun.")

        assertNotNull(result.recipe)
        assertTrue(result.warnings.single().startsWith("Could not read the line"))
    }

    @Test
    fun `without a film simulation there is no recipe`() {
        val result = RecipeTextParser.parse("Grain Effect: Weak, Small\nHighlight: +1")

        assertNull(result.recipe)
        assertTrue(result.warnings.any { it.contains("No film simulation") })
    }

    @Test
    fun `an unknown film simulation gives no recipe and names it`() {
        val result = RecipeTextParser.parse("Film Simulation: Kodak Gold 200")

        assertNull(result.recipe)
        assertTrue(result.warnings.single().contains("Kodak Gold 200"))
    }
}
