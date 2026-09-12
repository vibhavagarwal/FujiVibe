package com.fujivibe.render

import kotlin.random.Random

/**
 * A coarse grid of random values in `[0, 1]`, bilinearly sampled so cell edges blend into soft,
 * organic clumps instead of the hard block edges a nearest-neighbor lookup would produce.
 */
class NoiseGrid(val cellsWide: Int, val cellsHigh: Int, private val values: FloatArray) {

    init {
        require(cellsWide >= 1 && cellsHigh >= 1) {
            "cellsWide and cellsHigh must each be >= 1, got ${cellsWide}x$cellsHigh"
        }
        require(values.size == cellsWide * cellsHigh) {
            "Expected ${cellsWide * cellsHigh} values for ${cellsWide}x$cellsHigh, got ${values.size}"
        }
    }

    /** Bilinearly interpolated value for normalized coordinates `u, v` in `[0, 1]`. */
    fun sample(u: Float, v: Float): Float {
        val maxX = (cellsWide - 1).toFloat()
        val maxY = (cellsHigh - 1).toFloat()
        val xf = u.coerceIn(0f, 1f) * maxX
        val yf = v.coerceIn(0f, 1f) * maxY

        val x0 = xf.toInt().coerceIn(0, cellsWide - 1)
        val y0 = yf.toInt().coerceIn(0, cellsHigh - 1)
        val x1 = (x0 + 1).coerceAtMost(cellsWide - 1)
        val y1 = (y0 + 1).coerceAtMost(cellsHigh - 1)

        val xt = xf - x0
        val yt = yf - y0

        val top = lerp(valueAt(x0, y0), valueAt(x1, y0), xt)
        val bottom = lerp(valueAt(x0, y1), valueAt(x1, y1), xt)
        return lerp(top, bottom, yt)
    }

    private fun valueAt(x: Int, y: Int): Float = values[y * cellsWide + x]

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    companion object {
        /** Deterministic: the same [seed] and dimensions always produce the same grid. */
        fun generate(cellsWide: Int, cellsHigh: Int, seed: Long): NoiseGrid {
            val random = Random(seed)
            val values = FloatArray(cellsWide * cellsHigh) { random.nextFloat() }
            return NoiseGrid(cellsWide, cellsHigh, values)
        }
    }
}
