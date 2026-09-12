package com.fujivibe.render.tools

import com.fujivibe.render.Cube3DLut
import com.fujivibe.render.CubeLutParser
import java.io.File

/**
 * Derives `derived-luts/Nostalgic Neg Pixel sRGB.cube` from the source conversion LUT by running
 * every grid entry through [NostalgicNegCorrection.correct]. Run via
 * `./gradlew :render:bakeNostalgicNegPixelLut` (working directory pinned to the repo root — see
 * render/build.gradle.kts) after editing any of [NostalgicNegCorrection]'s tunable constants.
 */
object BakeNostalgicNegPixelLut {

    private val SOURCE_LUT =
        File("raw-assets/abpy-fujifilm-camera-profiles/provia conversion luts/Provia to Nostalgic Neg sRGB.cube")
    private val OUTPUT_LUT = File("derived-luts/Nostalgic Neg Pixel sRGB.cube")

    fun bake(source: Cube3DLut): Cube3DLut {
        val table = FloatArray(source.size * source.size * source.size * 3)
        var i = 0
        for (b in 0 until source.size) {
            for (g in 0 until source.size) {
                for (r in 0 until source.size) {
                    val corrected = NostalgicNegCorrection.correct(source.valueAt(r, g, b))
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
