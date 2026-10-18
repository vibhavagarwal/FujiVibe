package com.fujivibe.recipe

import com.fujivibe.camera.FujiFilmSim
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommenderTest {
    private fun recipe(
        id: String,
        sim: FujiFilmSim = FujiFilmSim.CLASSIC_CHROME,
        subjects: List<String> = emptyList(),
        light: List<String> = emptyList(),
        mood: List<String> = emptyList(),
        avoid: List<String> = emptyList(),
        clarity: Double? = null,
    ) = Recipe(
        id = id, name = id, kind = RecipeKind.OWN, baseSimulation = sim,
        settings = RecipeSettings(clarity = clarity), bestFor = BestFor(subjects, light, mood), avoid = avoid,
    )

    private val alpine = recipe("alpine", subjects = listOf("mountains", "snow", "landscape"), light = listOf("hard-light", "sunny-daylight"))
    private val provia = recipe("provia", FujiFilmSim.PROVIA, listOf("landscape", "street"), listOf("sunny-daylight"))
    private val portra = recipe("portra", FujiFilmSim.CLASSIC_NEG, listOf("portrait", "street", "travel", "landscape"), listOf("sunny-daylight", "overcast", "golden-hour"))
    private val night = recipe("night", FujiFilmSim.ETERNA, listOf("night", "street"), listOf("low-light", "artificial-light"))
    private val mono = recipe("mono", FujiFilmSim.ACROS, listOf("street", "documentary"), listOf("hard-light"))
    private val library = listOf(alpine, provia, portra, night, mono)

    @Test
    fun `the recipe covering most of what was ticked comes first, with the ticked tags as the reason`() {
        val s = Recommender.suggest(library, SuggestRequest(subjects = setOf("mountains", "snow"), light = setOf("hard-light")))

        assertEquals("alpine", s.picks.first().recipe.id)
        assertEquals(2, s.picks.first().slot)
        assertEquals("mountains, snow, hard light", s.picks.first().reason)
    }

    @Test
    fun `it fills the free slots and uses each recipe once`() {
        val s = Recommender.suggest(library, SuggestRequest(subjects = setOf("street")))

        assertEquals(listOf(2, 3, 4, 5), s.picks.map { it.slot }.sorted().take(4))
        assertEquals(s.picks.size, s.picks.map { it.recipe.id }.toSet().size)
    }

    @Test
    fun `a black and white recipe is reserved when asked for, and left out when not`() {
        val withMono = Recommender.suggest(library, SuggestRequest(subjects = setOf("mountains"), slots = listOf(2, 3, 4)))
        val without = Recommender.suggest(library, SuggestRequest(subjects = setOf("mountains"), includeMonochrome = false, slots = listOf(2, 3, 4)))

        assertTrue(withMono.picks.any { it.recipe.baseSimulation.isMonochrome })
        assertFalse(without.picks.any { it.recipe.baseSimulation.isMonochrome })
        assertEquals("alpine", without.picks.first().recipe.id)
    }

    @Test
    fun `locked slots are left alone and what they cover counts as covered`() {
        val s = Recommender.suggest(library, SuggestRequest(subjects = setOf("mountains", "street"), locked = mapOf(2 to alpine)))

        assertFalse(s.picks.any { it.slot == 2 })
        assertFalse(s.picks.any { it.recipe.id == "alpine" })
        assertTrue(s.picks.all { it.slot in 3..7 })
    }

    @Test
    fun `a recipe whose avoid text names a ticked subject ranks lower`() {
        val loud = recipe("loud", subjects = listOf("portrait", "travel"), avoid = listOf("portraits, skin tones clip orange"))
        val calm = recipe("calm", subjects = listOf("portrait"))

        val s = Recommender.suggest(listOf(loud, calm), SuggestRequest(subjects = setOf("portrait"), includeMonochrome = false, slots = listOf(2)))

        assertEquals("calm", s.picks.single().recipe.id)
    }

    @Test
    fun `without a filter, a recipe that leans on one is avoided`() {
        val dreamy = recipe("dreamy", subjects = listOf("portrait"), clarity = -4.0)
        val plain = recipe("plain", subjects = listOf("portrait"), clarity = 0.0)

        val with = Recommender.suggest(listOf(dreamy, plain), SuggestRequest(subjects = setOf("portrait"), includeMonochrome = false, slots = listOf(2), hasFilter = true))
        val without = Recommender.suggest(listOf(dreamy, plain), SuggestRequest(subjects = setOf("portrait"), includeMonochrome = false, slots = listOf(2), hasFilter = false))

        assertEquals("plain", without.picks.single().recipe.id)
        assertEquals(1, with.picks.size)
    }

    @Test
    fun `recipes on the same film simulation are kept apart`() {
        val a = recipe("a", FujiFilmSim.CLASSIC_CHROME, listOf("street"))
        val b = recipe("b", FujiFilmSim.CLASSIC_CHROME, listOf("street"))
        val c = recipe("c", FujiFilmSim.PROVIA, listOf("street"))

        val s = Recommender.suggest(listOf(a, b, c), SuggestRequest(subjects = setOf("street"), includeMonochrome = false, slots = listOf(2, 3)))

        assertEquals(setOf(FujiFilmSim.CLASSIC_CHROME, FujiFilmSim.PROVIA), s.picks.map { it.recipe.baseSimulation }.toSet())
    }

    @Test
    fun `a ticked tag no recipe has is reported as a gap`() {
        val s = Recommender.suggest(library, SuggestRequest(subjects = setOf("concerts", "street")))

        assertEquals(listOf("No recipe is tagged for concerts."), s.gaps)
    }

    @Test
    fun `with nothing ticked it still gives a spread, led by the broadest recipes`() {
        val s = Recommender.suggest(library, SuggestRequest())

        assertTrue(s.picks.isNotEmpty())
        assertEquals(emptyList<String>(), s.gaps)
    }

    @Test
    fun `fewer recipes than slots leaves the rest empty`() {
        val s = Recommender.suggest(listOf(alpine, provia), SuggestRequest(subjects = setOf("mountains"), includeMonochrome = false))

        assertEquals(2, s.picks.size)
    }
}

class RecommenderShippedSetTest {
    private val shipped = RecipeJson.parse(java.io.File("src/main/assets/recipes.json").readText())

    @Test
    fun `trips give sensible sets from the shipped recipes`() {
        val alps = Recommender.suggest(shipped, SuggestRequest(subjects = setOf("mountains", "snow", "landscape"), light = setOf("sunny-daylight", "golden-hour")))
        assertEquals("Soft Alpine", alps.picks.first().recipe.name)
        assertEquals(6, alps.picks.size)

        val night = Recommender.suggest(shipped, SuggestRequest(subjects = setOf("street", "night"), light = setOf("low-light", "artificial-light")))
        assertTrue(night.picks.any { it.recipe.name == "CineStill 800T" })

        val portraits = Recommender.suggest(shipped, SuggestRequest(subjects = setOf("portrait", "skin-tones")))
        assertFalse(portraits.picks.any { it.recipe.name == "Vibrant Arizona" })
    }
}
