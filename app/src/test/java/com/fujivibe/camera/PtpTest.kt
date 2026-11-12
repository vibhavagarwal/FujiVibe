package com.fujivibe.camera

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PtpTest {

    @Test
    fun `a command container is a 12-byte header then its parameters`() {
        val head = containerHead(PtpType.COMMAND, PtpOp.OPEN_SESSION, 0, intArrayOf(1), 0)

        assertArrayEquals(
            byteArrayOf(16, 0, 0, 0, 1, 0, 0x02, 0x10, 0, 0, 0, 0, 1, 0, 0, 0),
            head,
        )
    }

    @Test
    fun `a data container's length counts the payload that follows`() {
        val head = containerHead(PtpType.DATA, FujiOp.SEND_OBJECT2, 5, IntArray(0), 1000)

        assertEquals(1012, head.u32(0))
        assertEquals(PtpType.DATA, head.u16(4))
        assertEquals(FujiOp.SEND_OBJECT2, head.u16(6))
        assertEquals(5, head.u32(8))
    }

    @Test
    fun `a PTP string counts its terminator and is UTF-16LE`() {
        assertArrayEquals(byteArrayOf(3, 'a'.code.toByte(), 0, 'b'.code.toByte(), 0, 0, 0), packPtpString("ab"))
        assertArrayEquals(byteArrayOf(0), packPtpString(""))
    }

    @Test
    fun `the RAF ObjectInfo names the format, the size and the file`() {
        val info = rafObjectInfo(12_345)

        assertEquals(FUJI_RAF_FORMAT, info.u16(4))
        assertEquals(12_345, info.u32(8))
        // 52 fixed bytes, then the name and three empty strings.
        assertEquals(52 + packPtpString("FUP_FILE.dat").size + 3, info.size)
        assertEquals(PtpReader(info, 52).string(), "FUP_FILE.dat")
    }

    @Test
    fun `device info yields the model, firmware and supported operations and properties`() {
        val out = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN)
        out.putShort(100).putInt(0x0000000E).putShort(100)
        out.put(packPtpString("")) // vendor extension description
        out.putShort(0) // functional mode
        out.putInt(2).putShort(0x1001).putShort(0x900D.toShort()) // operations
        out.putInt(0) // events
        out.putInt(1).putShort(0xD185.toShort()) // properties
        out.putInt(0).putInt(0) // capture and image formats
        out.put(packPtpString("Fujifilm")).put(packPtpString("X-T5"))
        out.put(packPtpString("1.10")).put(packPtpString("1234"))

        val info = parseDeviceInfo(out.array().copyOf(out.position()))

        assertEquals("X-T5", info.model)
        assertEquals("1.10", info.deviceVersion)
        assertEquals(setOf(0x1001, 0x900D), info.operations)
        assertEquals(setOf(0xD185), info.properties)
    }
}
