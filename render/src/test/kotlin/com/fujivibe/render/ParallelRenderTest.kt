package com.fujivibe.render

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The render loops split big images into bands that run on several threads. These pin down that
 * doing so changes nothing about the output: an image large enough to be split must come out
 * exactly as a plain one-pixel-at-a-time reference computes it.
 */
class ParallelRenderTest {

    // 400x300 = 120,000 pixels: well over the per-band minimum, so it really is split.
    private val width = 400
    private val height = 300

    private fun randomImage(): PixelImage {
        val random = Random(7)
        return PixelImage(width, height, IntArray(width * height) { (0xFF shl 24) or (random.nextInt() and 0xFFFFFF) })
    }

    private fun gradientLut(size: Int = 5): Cube3DLut {
        // A deliberately non-identity LUT, so a wrong lookup can't pass by accident.
        val table = FloatArray(size * size * size * 3)
        var i = 0
        val max = (size - 1).toFloat()
        for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
            table[i++] = (g / max) * 0.8f
            table[i++] = (b / max) * 0.6f + 0.1f
            table[i++] = (r / max) * (r / max)
        }
        return Cube3DLut(size, table)
    }

    @Test
    fun `sampleInto matches sample exactly`() {
        val lut = gradientLut()
        val out = FloatArray(3)
        val random = Random(3)

        repeat(500) {
            val r = random.nextFloat()
            val g = random.nextFloat()
            val b = random.nextFloat()
            lut.sampleInto(r, g, b, out)
            val expected = lut.sample(r, g, b)
            assertEquals(expected.r, out[0])
            assertEquals(expected.g, out[1])
            assertEquals(expected.b, out[2])
        }
    }

    @Test
    fun `a LUT render of a large image equals the per-pixel reference`() = runTest {
        val lut = gradientLut()
        val source = randomImage()
        val pipeline = LutRenderPipeline(lutLoader = { lut })

        val result = pipeline.render(source, RenderSelection.Simulation(FilmSimulation.CLASSIC_NEG_PIXEL))

        assertEquals(source.pixels.size, result.pixels.size)
        for (i in source.pixels.indices) {
            val p = source.pixels[i]
            val rendered = lut.sample(((p ushr 16) and 0xFF) / 255f, ((p ushr 8) and 0xFF) / 255f, (p and 0xFF) / 255f)
            val expected = (0xFF shl 24) or
                ((rendered.r.coerceIn(0f, 1f) * 255f).roundToInt() shl 16) or
                ((rendered.g.coerceIn(0f, 1f) * 255f).roundToInt() shl 8) or
                (rendered.b.coerceIn(0f, 1f) * 255f).roundToInt()
            assertEquals(expected, result.pixels[i], "pixel $i differs")
        }
    }

    @Test
    fun `grain on a large image is deterministic and touches pixels across every band`() = runTest {
        val source = PixelImage(width, height, IntArray(width * height) { 0xFF808080.toInt() })
        val pipeline = GrainRenderPipeline(object : RenderPipeline {
            override suspend fun render(source: PixelImage, selection: RenderSelection) = source
        })
        val selection = RenderSelection.Simulation(FilmSimulation.CHARCOAL)

        val first = pipeline.render(source, selection)
        val second = pipeline.render(source, selection)

        assertEquals(first, second)
        // Spread across the whole image: the first, middle and last tenth all differ from flat gray.
        val tenth = source.pixels.size / 10
        for (start in listOf(0, tenth * 5, tenth * 9)) {
            val changed = (start until start + tenth).count { first.pixels[it] != source.pixels[it] }
            assertTrue(changed > tenth / 2, "only $changed of $tenth pixels changed from $start")
        }
    }
}
