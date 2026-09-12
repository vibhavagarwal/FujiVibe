package com.fujivibe.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class NoiseGridTest {

    @Test
    fun `the same seed and dimensions always produce the same grid`() {
        val first = NoiseGrid.generate(cellsWide = 4, cellsHigh = 3, seed = 42L)
        val second = NoiseGrid.generate(cellsWide = 4, cellsHigh = 3, seed = 42L)

        for (y in 0 until 3) {
            for (x in 0 until 4) {
                val u = x / 3f
                val v = y / 2f
                assertEquals(first.sample(u, v), second.sample(u, v), 1e-6f)
            }
        }
    }

    @Test
    fun `different seeds produce different grids`() {
        val first = NoiseGrid.generate(cellsWide = 4, cellsHigh = 3, seed = 1L)
        val second = NoiseGrid.generate(cellsWide = 4, cellsHigh = 3, seed = 2L)

        assertNotEquals(first.sample(0f, 0f), second.sample(0f, 0f))
    }

    @Test
    fun `sample at an exact grid corner returns that corner's stored value exactly`() {
        val grid = NoiseGrid(cellsWide = 2, cellsHigh = 2, values = floatArrayOf(0.1f, 0.9f, 0.3f, 0.7f))

        assertEquals(0.1f, grid.sample(0f, 0f), 1e-6f)
        assertEquals(0.9f, grid.sample(1f, 0f), 1e-6f)
        assertEquals(0.3f, grid.sample(0f, 1f), 1e-6f)
        assertEquals(0.7f, grid.sample(1f, 1f), 1e-6f)
    }

    @Test
    fun `sample interpolates linearly between horizontally adjacent grid points`() {
        val grid = NoiseGrid(cellsWide = 2, cellsHigh = 1, values = floatArrayOf(0.2f, 0.8f))

        val midpoint = grid.sample(0.5f, 0f)

        assertEquals(0.5f, midpoint, 1e-6f)
    }
}
