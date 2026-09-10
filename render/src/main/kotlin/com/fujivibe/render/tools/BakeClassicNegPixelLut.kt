package com.fujivibe.render.tools

import com.fujivibe.render.Cube3DLut
import com.fujivibe.render.CubeLutParser
import java.io.File

/**
 * Derives `derived-luts/Classic Neg Pixel sRGB.cube` from the source conversion LUT by running
 * every grid entry through [LutCorrection.correct]. Run via `./gradlew :render:run` (the `run`
 * task's working directory is pinned to the repo root — see render/build.gradle.kts) after
 * editing any of [LutCorrection]'s tunable constants.
 */
object BakeClassicNegPixelLut {

    private val SOURCE_LUT =
        File("raw-assets/abpy-fujifilm-camera-profiles/provia conversion luts/Provia to Classic Neg sRGB.cube")
    private val OUTPUT_LUT = File("derived-luts/Classic Neg Pixel sRGB.cube")

    fun bake(source: Cube3DLut): Cube3DLut {
        val table = FloatArray(source.size * source.size * source.size * 3)
        var i = 0
        for (b in 0 until source.size) {
            for (g in 0 until source.size) {
                for (r in 0 until source.size) {
                    val corrected = LutCorrection.correct(source.valueAt(r, g, b))
                    table[i++] = corrected.r
                    table[i++] = corrected.g
                    table[i++] = corrected.b
                }
            }
        }
        return Cube3DLut(source.size, table)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val source = CubeLutParser.parse(SOURCE_LUT.readText())
        val baked = bake(source)
        OUTPUT_LUT.parentFile.mkdirs()
        OUTPUT_LUT.writeText(CubeLutWriter.write(baked))
        println("Wrote ${OUTPUT_LUT.path} (${baked.size}^3 grid)")
    }
}
