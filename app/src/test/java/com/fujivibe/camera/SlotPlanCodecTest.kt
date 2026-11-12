package com.fujivibe.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SlotPlanCodecTest {

    @Test
    fun `a plan survives being written and read, in slot order`() {
        val plan = mapOf(4 to "koda", 2 to "portra")

        assertEquals("2=portra;4=koda", SlotPlanCodec.encode(plan))
        assertEquals(plan, SlotPlanCodec.decode(SlotPlanCodec.encode(plan)))
    }

    @Test
    fun `entries that are broken, for C1 or beyond C7 are dropped and the rest kept`() {
        val plan = SlotPlanCodec.decode("1=nope;2=portra;x=y;3=;9=late;4=koda;junk")

        assertEquals(mapOf(2 to "portra", 4 to "koda"), plan)
    }

    @Test
    fun `an empty text is an empty plan`() {
        assertEquals(emptyMap<Int, String>(), SlotPlanCodec.decode(""))
    }
}
