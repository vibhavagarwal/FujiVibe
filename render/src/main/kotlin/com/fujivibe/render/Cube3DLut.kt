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
        val out = FloatArray(3)
        sampleInto(r, g, b, out)
        return Rgb(out[0], out[1], out[2])
    }

    /**
     * [sample] without allocating: writes the interpolated red, green and blue into `out[0..2]`.
     * The render loops call this once per pixel, millions of times a photo, and Android's runtime
     * (unlike a desktop JVM) doesn't optimise away a per-pixel allocation, so the object-returning
     * [sample] was most of a render's cost. Same arithmetic as before, so results are identical.
     */
    fun sampleInto(r: Float, g: Float, b: Float, out: FloatArray) {
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

        val greenStride = size * 3
        val blueStride = size * size * 3
        val i000 = b0 * blueStride + g0 * greenStride + r0 * 3
        val i100 = b0 * blueStride + g0 * greenStride + r1 * 3
        val i010 = b0 * blueStride + g1 * greenStride + r0 * 3
        val i110 = b0 * blueStride + g1 * greenStride + r1 * 3
        val i001 = b1 * blueStride + g0 * greenStride + r0 * 3
        val i101 = b1 * blueStride + g0 * greenStride + r1 * 3
        val i011 = b1 * blueStride + g1 * greenStride + r0 * 3
        val i111 = b1 * blueStride + g1 * greenStride + r1 * 3

        for (c in 0 until 3) {
            val c00 = table[i000 + c] + (table[i100 + c] - table[i000 + c]) * rt
            val c10 = table[i010 + c] + (table[i110 + c] - table[i010 + c]) * rt
            val c01 = table[i001 + c] + (table[i101 + c] - table[i001 + c]) * rt
            val c11 = table[i011 + c] + (table[i111 + c] - table[i011 + c]) * rt

            val c0 = c00 + (c10 - c00) * gt
            val c1 = c01 + (c11 - c01) * gt

            out[c] = c0 + (c1 - c0) * bt
        }
    }
}
