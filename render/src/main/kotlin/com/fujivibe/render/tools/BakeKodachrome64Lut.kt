package com.fujivibe.render.tools

import com.fujivibe.render.Cube3DLut
import com.fujivibe.render.CubeLutParser
import com.fujivibe.render.Rgb
import java.io.File

/**
 * Derives `derived-luts/Kodachrome 64 sRGB.cube` from the pack's `classic chrome_sRGB.cube`.
 * That LUT expects scene-linear input (ADR 0004), but the app feeds it gamma-encoded JPEG pixels,
 * so each grid point's sRGB-encoded coordinate is linearized here, offline, before sampling the
 * source; the LUT's own built-in tone curve is then largely divided back out (see
 * [CLASSIC_CHROME_TONE_KEEP]) and the result run through [KodachromeCorrection.correct]. The
 * runtime pipeline stays a plain single-LUT lookup. Run via `./gradlew :render:bakeKodachrome64Lut` after editing any of
 * [KodachromeCorrection]'s constants.
 */
object BakeKodachrome64Lut {

    private val SOURCE_LUT =
        File("raw-assets/abpy-fujifilm-camera-profiles/cube lut/classic chrome_sRGB.cube")
    private val OUTPUT_LUT = File("derived-luts/Kodachrome 64 sRGB.cube")

    /**
     * The source LUT bakes in a strong raw-to-display tone curve (its neutral axis maps linear
     * 0.26 to ~0.29, far darker than a plain gamma encode). Applied to an already-toned JPEG that
     * double-processes tonality — near-black shadows and shifted hues. So each source output is
     * pulled back through the inverse of that neutral-axis curve (keeping only the LUT's *color*
     * character), then blended with the raw output by this fraction: 0 = colors only, 1 = the
     * source LUT's full tone as-is.
     */
    const val CLASSIC_CHROME_TONE_KEEP = 0.35f

    private const val NEUTRAL_TABLE_SIZE = 1024

    fun srgbToLinear(encoded: Float): Float =
        if (encoded <= 0.04045f) {
            encoded / 12.92f
        } else {
            Math.pow(((encoded + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }

    /**
     * Monotone table of the source's neutral-axis output, indexed by sRGB-encoded input, used to
     * invert the LUT's built-in tone curve: `neutralize(o)` is the encoded input that a neutral
     * pixel would need to produce output `o`.
     */
    private class NeutralAxis(source: Cube3DLut) {
        private val outputs = FloatArray(NEUTRAL_TABLE_SIZE + 1)

        init {
            var runningMax = 0f
            for (i in outputs.indices) {
                val linear = srgbToLinear(i / NEUTRAL_TABLE_SIZE.toFloat())
                runningMax = maxOf(runningMax, source.sample(linear, linear, linear).g)
                outputs[i] = runningMax
            }
        }

        fun neutralize(output: Float): Float {
            if (output <= outputs.first()) return 0f
            if (output >= outputs.last()) return 1f
            var lo = 0
            var hi = outputs.size - 1
            while (hi - lo > 1) {
                val mid = (lo + hi) / 2
                if (outputs[mid] < output) lo = mid else hi = mid
            }
            val span = outputs[hi] - outputs[lo]
            val t = if (span <= 0f) 1f else (output - outputs[lo]) / span
            return (lo + t * (hi - lo)) / NEUTRAL_TABLE_SIZE.toFloat()
        }
    }

    fun bake(source: Cube3DLut, toneKeep: Float = CLASSIC_CHROME_TONE_KEEP): Cube3DLut {
        val size = source.size
        val maxIndex = (size - 1).toFloat()
        val neutralAxis = NeutralAxis(source)
        val table = FloatArray(size * size * size * 3)
        var i = 0
        for (b in 0 until size) {
            for (g in 0 until size) {
                for (r in 0 until size) {
                    val raw = source.sample(
                        srgbToLinear(r / maxIndex),
                        srgbToLinear(g / maxIndex),
                        srgbToLinear(b / maxIndex),
                    )
                    val blended = Rgb(
                        lerp(neutralAxis.neutralize(raw.r), raw.r, toneKeep),
                        lerp(neutralAxis.neutralize(raw.g), raw.g, toneKeep),
                        lerp(neutralAxis.neutralize(raw.b), raw.b, toneKeep),
                    )
                    val corrected = KodachromeCorrection.lightenSkin(
                        input = Rgb(r / maxIndex, g / maxIndex, b / maxIndex),
                        corrected = KodachromeCorrection.correct(blended),
                    )
                    table[i++] = corrected.r
                    table[i++] = corrected.g
                    table[i++] = corrected.b
                }
            }
        }
        return Cube3DLut(size, table)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    @JvmStatic
    fun main(args: Array<String>) {
        val source = CubeLutParser.parse(SOURCE_LUT.readText())
        val baked = bake(source)
        OUTPUT_LUT.parentFile.mkdirs()
        OUTPUT_LUT.writeText(CubeLutWriter.write(baked))
        println("Wrote ${OUTPUT_LUT.path} (${baked.size}^3 grid)")
    }
}
