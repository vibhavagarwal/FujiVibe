package com.fujivibe.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FilmSimulationTest {

    @Test
    fun `every look has a short name, so a thumbnail can be labelled without room for its full name`() {
        for (simulation in FilmSimulation.entries) {
            assertTrue(simulation.shortName.isNotBlank(), "${simulation.name} has no short name")
        }
    }

    @Test
    fun `short names are unique, and none is O, which stands for Original`() {
        val shortNames = FilmSimulation.entries.map { it.shortName }

        assertEquals(shortNames.size, shortNames.toSet().size)
        assertTrue("O" !in shortNames)
    }
}
