package com.fujivibe.gallery

import com.fujivibe.render.FilmSimulation
import com.fujivibe.render.RenderSelection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LookFileNameTest {

    @Test
    fun `labels are file-safe versions of the look's name`() {
        assertEquals("Original", lookFileLabel(RenderSelection.Original))
        assertEquals("Charcoal", lookFileLabel(RenderSelection.Simulation(FilmSimulation.CHARCOAL)))
        assertEquals("ClassicNegPixel", lookFileLabel(RenderSelection.Simulation(FilmSimulation.CLASSIC_NEG_PIXEL)))
        assertEquals("NostalgicNegPixel", lookFileLabel(RenderSelection.Simulation(FilmSimulation.NOSTALGIC_NEG_PIXEL)))
        assertEquals("Koda64Pixel", lookFileLabel(RenderSelection.Simulation(FilmSimulation.KODA64_PIXEL)))
    }

    @Test
    fun `every look has its own label, so a file name identifies exactly one look`() {
        val labels = listOf(RenderSelection.Original) + FilmSimulation.entries.map { RenderSelection.Simulation(it) }

        assertEquals(labels.size, labels.map(::lookFileLabel).toSet().size)
    }

    @Test
    fun `a file name's look can be read back for every look`() {
        val selections = listOf(RenderSelection.Original) + FilmSimulation.entries.map { RenderSelection.Simulation(it) }

        for (selection in selections) {
            val name = "${lookFileLabel(selection)}_20231114_221320.jpg"
            assertEquals(selection, lookFromFileName(name), "round trip failed for $name")
        }
    }

    @Test
    fun `an older FujiVibe-prefixed name, or any unknown prefix, has no look`() {
        assertNull(lookFromFileName("FujiVibe_20231114_221320.jpg"))
        assertNull(lookFromFileName("IMG_1234.jpg"))
        assertNull(lookFromFileName("Charcoal.jpg"))
        assertNull(lookFromFileName(""))
    }
}
