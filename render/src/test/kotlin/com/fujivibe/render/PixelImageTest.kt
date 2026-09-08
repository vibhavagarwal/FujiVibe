package com.fujivibe.render

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PixelImageTest {

    @Test
    fun `mutating the caller's array after construction does not change the PixelImage`() {
        val callerOwnedPixels = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        val image = PixelImage(width = 2, height = 1, pixels = callerOwnedPixels)

        callerOwnedPixels[0] = 0xFFFF0000.toInt()

        assertEquals(0xFF000000.toInt(), image.pixels[0])
    }
}
