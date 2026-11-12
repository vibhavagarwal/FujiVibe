package com.fujivibe.recipe

import com.fujivibe.camera.FujiFilmSim
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecipeTraitsTest {
    private fun recipe(
        id: String,
        sim: FujiFilmSim = FujiFilmSim.CLASSIC_CHROME,
        settings: RecipeSettings = RecipeSettings(),
        best: BestFor = BestFor(),
    ) = Recipe(id = id, name = id, kind = RecipeKind.OWN, baseSimulation = sim, settings = settings, bestFor = best)

    @Test
    fun `chips lead with black and white then moods then grain`() {
        val r = recipe(
            "a", FujiFilmSim.ACROS,
            RecipeSettings(grain = Grain(GrainStrength.STRONG, GrainSize.LARGE)),
            BestFor(mood = listOf("high-contrast", "gritty", "cinematic", "clean")),
        )
        assertEquals(listOf("B&W", "High contrast", "Gritty", "Cinematic", "Grain"), r.characterChips())
    }

    @Test
    fun `a recipe with no tags and no grain has no chips`() {
        assertEquals(emptyList<String>(), recipe("a", settings = RecipeSettings(grain = Grain(GrainStrength.OFF))).characterChips())
    }

    @Test
    fun `setting lines show only what the recipe sets, with signs`() {
        val r = recipe(
            "a",
            settings = RecipeSettings(
                dynamicRange = DynamicRange.DR400,
                grain = Grain(GrainStrength.WEAK, GrainSize.SMALL),
                colorChrome = Level.STRONG,
                whiteBalance = WhiteBalance(WhiteBalanceMode.AUTO, shiftR = 2, shiftB = -4),
                highlight = -1.0,
                shadow = 0.5,
                color = 0.0,
            ),
        )
        val lines = r.settingLines().toMap()
        assertEquals("Classic Chrome", lines["Film simulation"])
        assertEquals("DR400", lines["Dynamic range"])
        assertEquals("Weak, small", lines["Grain"])
        assertEquals("Strong", lines["Color Chrome Effect"])
        assertEquals("Auto, R +2, B -4", lines["White balance"])
        assertEquals("-1", lines["Highlight tone"])
        assertEquals("+0.5", lines["Shadow tone"])
        assertEquals("0", lines["Color"])
        assertFalse("Sharpness" in lines)
    }

    @Test
    fun `filter needs any tag within a group and every group`() {
        val coast = recipe("coast", best = BestFor(subjects = listOf("coast", "water"), mood = listOf("cool")))
        val city = recipe("city", FujiFilmSim.ACROS, best = BestFor(subjects = listOf("street"), mood = listOf("gritty")))
        assertTrue(RecipeFilter().matches(coast))
        assertTrue(RecipeFilter(subjects = setOf("water", "street")).matches(coast))
        assertTrue(RecipeFilter(subjects = setOf("water", "street")).matches(city))
        assertFalse(RecipeFilter(subjects = setOf("water"), mood = setOf("gritty")).matches(coast))
        assertTrue(RecipeFilter(monochrome = true).matches(city))
        assertFalse(RecipeFilter(monochrome = true).matches(coast))
        assertTrue(RecipeFilter(monochrome = false).matches(coast))
    }

    @Test
    fun `tags come most used first`() {
        val rs = listOf(
            recipe("a", best = BestFor(subjects = listOf("street", "travel"))),
            recipe("b", best = BestFor(subjects = listOf("travel"))),
            recipe("c", best = BestFor(subjects = listOf("coast", "travel"))),
        )
        assertEquals(listOf("travel", "coast", "street"), rs.tagsBy { it.bestFor.subjects })
    }

    @Test
    fun `tag label reads as words`() {
        assertEquals("Autumn leaves", tagLabel("autumn-leaves"))
    }
}
