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

        val afterThirdNext = afterSecondNext.next()
        assertEquals(RenderSelection.Simulation(FilmSimulation.KODA64_PIXEL), afterThirdNext.current)
        assertEquals("Koda64Pixel", afterThirdNext.label)
    }

    @Test
    fun `next wraps from the last Film Simulation back to Original`() {
        val lastEntry = ReviewCycle.start().next().next().next()

        val wrapped = lastEntry.next()

        assertEquals(RenderSelection.Original, wrapped.current)
    }

    @Test
    fun `previous wraps from Original back to the last Film Simulation`() {
        val cycle = ReviewCycle.start()

        val wrapped = cycle.previous()

        assertEquals(RenderSelection.Simulation(FilmSimulation.KODA64_PIXEL), wrapped.current)
    }

    @Test
    fun `previous is the exact inverse of next`() {
        val cycle = ReviewCycle.start().next()

        assertEquals(cycle, cycle.next().previous())
        assertEquals(cycle, cycle.previous().next())
    }

    @Test
    fun `position and count track the place in the cycle`() {
        val cycle = ReviewCycle.start()

        assertEquals(0, cycle.position)
        assertEquals(1 + FilmSimulation.entries.size, cycle.count)
        assertEquals(2, cycle.next().next().position)
    }

    @Test
    fun `at restores a cycle from its position`() {
        val cycle = ReviewCycle.start().next()

        assertEquals(cycle, ReviewCycle.at(cycle.position))
    }

    @Test
    fun `at falls back to start for an out-of-range position`() {
        assertEquals(ReviewCycle.start(), ReviewCycle.at(99))
        assertEquals(ReviewCycle.start(), ReviewCycle.at(-1))
    }
}
