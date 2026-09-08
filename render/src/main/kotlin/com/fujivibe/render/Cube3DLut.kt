package com.fujivibe.render

/**
 * A parsed 3D LUT: a `size` x `size` x `size` grid of [Rgb] values, stored red-fastest
 * (then green, then blue) per the .cube spec's data ordering.
 */
class Cube3DLut(val size: Int, private val table: FloatArray) {

    init {
        require(size > 0) { "size must be positive, was $size" }
        require(table.size == size * size * size * 3) {
            "Expected ${size * size * size * 3} values for size $size, got ${table.size}"
        }
    }

    /** The grid value at exact indices `r, g, b`, each in `0 until size`. */
    fun valueAt(r: Int, g: Int, b: Int): Rgb {
        val base = ((b * size + g) * size + r) * 3
        return Rgb(table[base], table[base + 1], table[base + 2])
    }

    /** Trilinearly interpolated value for `r, g, b` in `[0, 1]`. */
    fun sample(r: Float, g: Float, b: Float): Rgb {
        val maxIndex = (size - 1).toFloat()
        val rf = r.coerceIn(0f, 1f) * maxIndex
        val gf = g.coerceIn(0f, 1f) * maxIndex
        val bf = b.coerceIn(0f, 1f) * maxIndex

        val r0 = rf.toInt().coerceIn(0, size - 1)
        val g0 = gf.toInt().coerceIn(0, size - 1)
        val b0 = bf.toInt().coerceIn(0, size - 1)
        val r1 = (r0 + 1).coerceAtMost(size - 1)
        val g1 = (g0 + 1).coerceAtMost(size - 1)
        val b1 = (b0 + 1).coerceAtMost(size - 1)

        val rt = rf - r0
        val gt = gf - g0
        val bt = bf - b0

        val c00 = lerp(valueAt(r0, g0, b0), valueAt(r1, g0, b0), rt)
        val c10 = lerp(valueAt(r0, g1, b0), valueAt(r1, g1, b0), rt)
        val c01 = lerp(valueAt(r0, g0, b1), valueAt(r1, g0, b1), rt)
        val c11 = lerp(valueAt(r0, g1, b1), valueAt(r1, g1, b1), rt)

        val c0 = lerp(c00, c10, gt)
        val c1 = lerp(c01, c11, gt)

        return lerp(c0, c1, bt)
    }

    private fun lerp(a: Rgb, b: Rgb, t: Float): Rgb = Rgb(
        a.r + (b.r - a.r) * t,
        a.g + (b.g - a.g) * t,
        a.b + (b.b - a.b) * t,
    )
}
