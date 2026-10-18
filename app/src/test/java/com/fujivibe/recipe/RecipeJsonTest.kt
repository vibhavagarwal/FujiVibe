package com.fujivibe.recipe

import com.fujivibe.camera.FujiFilmSim
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class RecipeJsonTest {

    private val one = """
        {"_about": "ignored", "recipes": [{
          "id": "k", "name": "K", "kind": "community", "baseSimulation": "classic-chrome",
          "settings": {
            "dynamicRange": "dr200", "dRangePriority": null,
            "grain": {"strength": "weak", "size": "small"},
            "colorChrome": "strong", "colorChromeFxBlue": "off",
            "whiteBalance": {"mode": "daylight", "kelvin": null, "shiftR": 2, "shiftB": -5},
            "highlight": 0, "shadow": 0.5, "color": null, "sharpness": 1, "highIsoNr": -4, "clarity": 3
          },
          "bestFor": {"subjects": ["landscape"], "light": ["sunny-daylight"], "mood": ["warm"]},
          "avoid": ["tungsten light"], "recommendationText": "Slide film look."
        }]}
    """

    @Test
    fun `a recipe is read with its settings and its best-use text`() {
        val r = RecipeJson.parse(one).single()

        assertEquals("K", r.name)
        assertEquals(RecipeKind.COMMUNITY, r.kind)
        assertEquals(FujiFilmSim.CLASSIC_CHROME, r.baseSimulation)
        assertEquals(DynamicRange.DR200, r.settings.dynamicRange)
        assertEquals(Grain(GrainStrength.WEAK, GrainSize.SMALL), r.settings.grain)
        assertEquals(0.5, r.settings.shadow)
        assertNull(r.settings.color)
        assertEquals(WhiteBalance(WhiteBalanceMode.DAYLIGHT, null, 2, -5), r.settings.whiteBalance)
        assertEquals(listOf("landscape"), r.bestFor.subjects)
        assertEquals("Slide film look.", r.recommendationText)
    }

    @Test
    fun `an unknown film simulation is an error naming the recipe`() {
        val bad = one.replace("classic-chrome", "kodak-gold")

        val e = assertThrows(IllegalArgumentException::class.java) { RecipeJson.parse(bad) }

        assertTrue(e.message!!.contains("k") && e.message!!.contains("kodak-gold"))
    }

    @Test
    fun `the shipped recipe set loads, with unique ids and a recommendation text for each`() {
        val recipes = RecipeJson.parse(File("src/main/assets/recipes.json").readText())

        assertEquals(23, recipes.size)
        assertEquals(recipes.size, recipes.map { it.id }.toSet().size)
        recipes.forEach { assertTrue(it.recommendationText.isNotBlank(), "${it.id} has no recommendation text") }
        recipes.filter { it.kind == RecipeKind.COMMUNITY }.forEach {
            assertTrue(it.bestFor.subjects.isNotEmpty() && it.bestFor.light.isNotEmpty(), "${it.id} has no best-use tags")
        }
    }
}
