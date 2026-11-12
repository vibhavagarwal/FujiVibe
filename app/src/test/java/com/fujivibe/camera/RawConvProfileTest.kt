package com.fujivibe.camera

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RawConvProfileTest {

    /** A profile with [count] parameters (each its own index times 10), behind an arbitrary header. */
    private fun profile(count: Int = 30, header: Int = 20): RawConvProfile {
        val b = ByteBuffer.allocate(header + count * 4).order(ByteOrder.LITTLE_ENDIAN)
        b.putShort(count.toShort())
        b.position(header)
        repeat(count) { b.putInt(it * 10) }
        return RawConvProfile(b.array())
    }

    @Test
    fun `parameters are read from the table at the end of the block`() {
        val p = profile()

        assertEquals(80, p.field(RawConvProfile.FILM_SIMULATION))
        assertEquals(290, p.field(29))
    }

    @Test
    fun `changing a film simulation leaves every other byte alone`() {
        val p = profile()

        val changed = p.withFilmSimulation(FujiFilmSim.CLASSIC_CHROME.code)

        assertEquals(0x0B, changed.field(RawConvProfile.FILM_SIMULATION))
        val untouched = p.bytes.copyOf().also { it[it.size - 30 * 4 + 8 * 4] = 0x0B }
        assertArrayEquals(untouched, changed.bytes)
    }

    @Test
    fun `a change makes a copy and leaves the original profile as it was`() {
        val p = profile()

        p.withFilmSimulation(2)

        assertEquals(80, p.field(RawConvProfile.FILM_SIMULATION))
    }

    @Test
    fun `asking for full size sets the image size and quality parameters only`() {
        val p = profile()

        val full = p.withFullSize()

        assertEquals(7, full.field(RawConvProfile.IMAGE_SIZE))
        assertEquals(2, full.field(RawConvProfile.IMAGE_QUALITY))
        assertEquals(p.field(RawConvProfile.FILM_SIMULATION), full.field(RawConvProfile.FILM_SIMULATION))
    }

    @Test
    fun `a parameter that is not in the block is refused`() {
        assertThrows(IllegalArgumentException::class.java) { profile(count = 5).field(8) }
    }

    @Test
    fun `a block too short for its parameter count is refused`() {
        assertThrows(IllegalArgumentException::class.java) { RawConvProfile(byteArrayOf(30, 0, 1, 2)) }
    }
}
