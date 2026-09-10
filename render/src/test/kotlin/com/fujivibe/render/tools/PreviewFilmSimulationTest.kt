package com.fujivibe.render.tools

import com.fujivibe.render.PixelImage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class PreviewFilmSimulationTest {

    @Test
    fun `toPixelImage reads a BufferedImage's ARGB pixels in row-major order`() {
        val buffered = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        buffered.setRGB(0, 0, 0xFFFF0000.toInt()) // top-left: opaque red
        buffered.setRGB(1, 0, 0xFF00FF00.toInt()) // top-right: opaque green
        buffered.setRGB(0, 1, 0xFF0000FF.toInt()) // bottom-left: opaque blue
        buffered.setRGB(1, 1, 0x00000000) // bottom-right: transparent black

        val pixelImage = PreviewFilmSimulation.toPixelImage(buffered)

        assertEquals(2, pixelImage.width)
        assertEquals(2, pixelImage.height)
        assertEquals(0xFFFF0000.toInt(), pixelImage.pixels[0])
        assertEquals(0xFF00FF00.toInt(), pixelImage.pixels[1])
        assertEquals(0xFF0000FF.toInt(), pixelImage.pixels[2])
        assertEquals(0x00000000, pixelImage.pixels[3])
    }

    @Test
    fun `toBufferedImage and toPixelImage round-trip a PixelImage`() {
        val original = PixelImage(
            width = 2,
            height = 1,
            pixels = intArrayOf(0xFF112233.toInt(), 0xFFAABBCC.toInt()),
        )

        val roundTripped = PreviewFilmSimulation.toPixelImage(PreviewFilmSimulation.toBufferedImage(original))

        assertEquals(original, roundTripped)
    }

    @Test
    fun `sideBySide places left image's rows before right image's rows, per row`() {
        val left = PixelImage(width = 1, height = 2, pixels = intArrayOf(0xA, 0xB))
        val right = PixelImage(width = 1, height = 2, pixels = intArrayOf(0xC, 0xD))

        val composite = PreviewFilmSimulation.sideBySide(left, right)

        assertEquals(2, composite.width)
        assertEquals(2, composite.height)
        assertEquals(PixelImage(width = 2, height = 2, pixels = intArrayOf(0xA, 0xC, 0xB, 0xD)), composite)
    }

    @Test
    fun `sideBySideAll concatenates three or more images left to right, in order`() {
        val first = PixelImage(width = 1, height = 1, pixels = intArrayOf(0xA))
        val second = PixelImage(width = 1, height = 1, pixels = intArrayOf(0xB))
        val third = PixelImage(width = 1, height = 1, pixels = intArrayOf(0xC))

        val composite = PreviewFilmSimulation.sideBySideAll(listOf(first, second, third))

        assertEquals(PixelImage(width = 3, height = 1, pixels = intArrayOf(0xA, 0xB, 0xC)), composite)
    }

    @Test
    fun `sideBySide rejects images of different heights`() {
        val left = PixelImage(width = 1, height = 2, pixels = intArrayOf(0xA, 0xB))
        val right = PixelImage(width = 1, height = 1, pixels = intArrayOf(0xC))

        assertThrows<IllegalArgumentException> { PreviewFilmSimulation.sideBySide(left, right) }
    }

    @Test
    fun `toOpaqueBufferedImage can be written and read back as a JPEG despite the source having an alpha channel`() {
        val image = PixelImage(
            width = 2,
            height = 2,
            pixels = intArrayOf(
                0xFFFF0000.toInt(), 0xFF00FF00.toInt(),
                0xFF0000FF.toInt(), 0xFFFFFFFF.toInt(),
            ),
        )
        val bytes = ByteArrayOutputStream()

        val wrote = ImageIO.write(PreviewFilmSimulation.toOpaqueBufferedImage(image), "jpg", bytes)

        assertTrue(wrote, "ImageIO should have found a JPEG writer for an opaque RGB image")
        val readBack = ImageIO.read(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(2, readBack.width)
        assertEquals(2, readBack.height)
    }
}
