package com.fujivibe.review

import com.fujivibe.render.FilmSimulation
import com.fujivibe.render.RenderSelection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SavedLooksTest {

    private val classicNeg = RenderSelection.Simulation(FilmSimulation.CLASSIC_NEG_PIXEL)

    @Test
    fun `nothing saved allows every Export and closes with Discard`() {
        val saved = SavedLooks.NONE

        assertTrue(saved.canExport(RenderSelection.Original))
        assertTrue(saved.canExport(classicNeg))
        assertEquals("Discard", saved.closeActionLabel)
    }

    @Test
    fun `a saved look blocks re-Export of only that look and closes with Done`() {
        val saved = SavedLooks.NONE + classicNeg

        assertTrue(classicNeg in saved)
        assertFalse(saved.canExport(classicNeg))
        assertTrue(saved.canExport(RenderSelection.Original))
        assertEquals("Done", saved.closeActionLabel)
    }

    @Test
    fun `round-trips through saved-state keys`() {
        val saved = SavedLooks.NONE + RenderSelection.Original + classicNeg

        assertEquals(saved, SavedLooks.fromKeys(saved.toKeys()))
    }

    @Test
    fun `unknown keys are dropped on restore`() {
        assertEquals(SavedLooks.NONE + classicNeg, SavedLooks.fromKeys(listOf("GONE", "CLASSIC_NEG_PIXEL")))
    }
}
