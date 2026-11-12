package com.fujivibe.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LoadHistoryTest {
    private val a = LoadEntry(1000, mapOf(2 to "reggies-portra", 3 to "pacific-blues"))
    private val b = LoadEntry(2000, mapOf(2 to "newspaper"))

    @Test
    fun `entries round trip`() {
        assertEquals(listOf(b, a), LoadHistoryCodec.decode(LoadHistoryCodec.encode(listOf(b, a))))
    }

    @Test
    fun `a bad entry is dropped and the rest kept`() {
        assertEquals(listOf(a), LoadHistoryCodec.decode("garbage|x:2=y|" + LoadHistoryCodec.encode(listOf(a)) + "|3000:"))
    }

    @Test
    fun `nothing stored decodes to nothing`() {
        assertEquals(emptyList<LoadEntry>(), LoadHistoryCodec.decode(""))
    }

    @Test
    fun `newest goes first`() {
        assertEquals(listOf(b, a), listOf(a).withRecorded(b))
    }

    @Test
    fun `the same recipes again replace the latest entry`() {
        val again = LoadEntry(3000, a.plan)
        assertEquals(listOf(again, b), listOf(a, b).withRecorded(again))
    }

    @Test
    fun `only the newest are kept`() {
        val many = (1..5).map { LoadEntry(it.toLong(), mapOf(2 to "r$it")) }
        assertEquals(listOf(5L, 4L, 3L), many.fold(emptyList<LoadEntry>()) { h, e -> h.withRecorded(e, max = 3) }.map { it.at })
    }
}
