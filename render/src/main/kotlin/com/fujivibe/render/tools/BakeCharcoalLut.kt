package com.fujivibe.render.tools

import com.fujivibe.render.Cube3DLut
import com.fujivibe.render.Rgb
import java.io.File

/**
 * Bakes `derived-luts/Charcoal sRGB.cube` from [CharcoalCorrection] alone. Unlike the other derived
 * LUTs there is no source `.cube`: the look is a monochrome conversion plus a tone curve, so the
 * identity grid (sRGB-encoded coordinates in, since the app feeds the LUT gamma-encoded JPEG
 * pixels) is run straight through the correction. The runtime pipeline stays a plain single-LUT
 * lookup. Run via `./gradlew :render:bakeCharcoalLut` after editing any of [CharcoalCorrection]'s
 * constants.
 */
object BakeCharcoalLut {

    private val OUTPUT_LUT = File("derived-luts/Charcoal sRGB.cube")

    const val GRID_SIZE = 32

    fun bake(size: Int = GRID_SIZE): Cube3DLut {
        val maxIndex = (size - 1).toFloat()
        val table = FloatArray(size * size * size * 3)
        var i = 0
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val corrected = CharcoalCorrection.correct(Rgb(r / maxIndex, g / maxIndex, b / maxIndex))
                    table[i++] = corrected.r
                    table[i++] = corrected.g
                    table[i++] = corrected.b
                }
            }
        }
        return Cube3DLut(size, table)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val baked = bake()
        OUTPUT_LUT.parentFile.mkdirs()
        OUTPUT_LUT.writeText(CubeLutWriter.write(baked))
        println("Wrote ${OUTPUT_LUT.path} (${baked.size}^3 grid)")
    }
}
