package com.fujivibe.review

import com.fujivibe.render.FilmSimulation
import com.fujivibe.render.RenderSelection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReviewCycleTest {

    @Test
    fun `starts at Original`() {
        val cycle = ReviewCycle.start()

        assertEquals(RenderSelection.Original, cycle.current)
        assertEquals("Original", cycle.label)
    }

    @Test
    fun `next steps through the launch Film Simulations in registry order`() {
        val cycle = ReviewCycle.start()

        val afterFirstNext = cycle.next()
        assertEquals(RenderSelection.Simulation(FilmSimulation.CLASSIC_NEG_PIXEL), afterFirstNext.current)
        assertEquals("Classic Neg. (Pixel)", afterFirstNext.label)

        val afterSecondNext = afterFirstNext.next()
        assertEquals(RenderSelection.Simulation(FilmSimulation.NOSTALGIC_NEG_PIXEL), afterSecondNext.current)
        assertEquals("Nostalgic Neg. (Pixel)", afterSecondNext.label)
    }

    @Test
    fun `next wraps from the last Film Simulation back to Original`() {
        val lastEntry = ReviewCycle.start().next().next()

        val wrapped = lastEntry.next()

        assertEquals(RenderSelection.Original, wrapped.current)
    }

    @Test
    fun `previous wraps from Original back to the last Film Simulation`() {
        val cycle = ReviewCycle.start()

        val wrapped = cycle.previous()

        assertEquals(RenderSelection.Simulation(FilmSimulation.NOSTALGIC_NEG_PIXEL), wrapped.current)
    }

    @Test
    fun `previous is the exact inverse of next`() {
        val cycle = ReviewCycle.start().next()

        assertEquals(cycle, cycle.next().previous())
        assertEquals(cycle, cycle.previous().next())
    }
}
