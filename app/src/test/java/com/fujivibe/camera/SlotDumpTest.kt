package com.fujivibe.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlotDumpTest {

    private fun u16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())

    private fun slot() = SlotDump(
        3,
        linkedMapOf(
            FujiProp.SLOT_NAME to packPtpString("Kodachrome"),
            0xD18E to u16(7),
            0xD19C to u16(10000),
            0xD1A1 to u16(0x2000),
        ),
    )

    @Test
    fun `a slot's name is read from its PTP string`() {
        assertEquals("Kodachrome", slot().name)
    }

    @Test
    fun `an unnamed slot has an empty name`() {
        assertEquals("", SlotDump(1, mapOf(FujiProp.SLOT_NAME to byteArrayOf(1, 0, 0))).name)
    }

    @Test
    fun `a 16-bit setting reads as a number, and one that is not is null`() {
        assertEquals(7, slot().value(0xD18E))
        assertEquals(10000, slot().value(0xD19C))
        assertNull(slot().value(FujiProp.SLOT_NAME))
        assertNull(slot().value(0xD1A5))
    }

    @Test
    fun `the log line names the slot and lists each setting`() {
        val line = slot().describe()

        assertTrue(line.startsWith("C3 \"Kodachrome\": D18E=7"), line)
        assertTrue(line.contains("D19C=10000"), line)
        assertTrue(line.contains("D1A5=-"), line)
    }

    @Test
    fun `the backup line keeps every property as hex so it can be restored exactly`() {
        val line = slot().backupLine()

        assertTrue(line.startsWith("slot 3 D18D="), line)
        assertTrue(line.contains("D18E=0700"), line)
        assertTrue(line.contains("D19C=1027"), line)
    }

    @Test
    fun `a backup line reads back into the same slot`() {
        val back = SlotDump.fromBackupLine(slot().backupLine())!!

        assertEquals(3, back.number)
        assertEquals("Kodachrome", back.name)
        assertEquals(7, back.value(0xD18E))
        assertEquals(10000, back.value(0xD19C))
        assertEquals(slot().backupLine(), back.backupLine())
    }

    @Test
    fun `a line that is not a backup line is not read`() {
        assertNull(SlotDump.fromBackupLine("hello"))
        assertNull(SlotDump.fromBackupLine("slot x D18E=0700"))
    }
}
