package com.fujivibe.render.tools

import com.fujivibe.render.Cube3DLut

/** Serializes a [Cube3DLut] back to the Adobe `.cube` text format `CubeLutParser` reads. */
object CubeLutWriter {

    fun write(lut: Cube3DLut): String = buildString {
        appendLine("LUT_3D_SIZE ${lut.size}")
        for (b in 0 until lut.size) {
            for (g in 0 until lut.size) {
                for (r in 0 until lut.size) {
                    val rgb = lut.valueAt(r, g, b)
                    appendLine("${rgb.r} ${rgb.g} ${rgb.b}")
                }
            }
        }
    }
}
