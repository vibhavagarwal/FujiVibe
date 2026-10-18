package com.fujivibe.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SlotNotesTest {
    @Test
    fun `a refused setting is named`() {
        assertEquals("The camera would not accept Dynamic Range.", SlotNotes.plain("The camera refused 0xD190 (wanted ffff)"))
    }

    @Test
    fun `a setting that did not stay is named`() {
        assertEquals("White balance red shift did not stay as written.", SlotNotes.plain("Did not read back: 0xD19A: wrote 0200, read 0000"))
    }

    @Test
    fun `an unknown property keeps its code`() {
        assertEquals("The camera would not accept setting 0xD1FF.", SlotNotes.plain("The camera refused 0xD1FF (wanted 01)"))
    }

    @Test
    fun `known notes become sentences and unknown ones pass through`() {
        assertEquals("The recipe gives no white balance mode, so Auto is used.", SlotNotes.plain("White balance mode not given; Auto used"))
        assertEquals(
            "High ISO noise reduction -3 is not a value the app can write. It is left at 0; set it on the camera if you want it.",
            SlotNotes.plain("High ISO NR -3 is not written (left at 0)"),
        )
        assertEquals("Something new", SlotNotes.plain("Something new"))
    }
}
