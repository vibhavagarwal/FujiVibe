package com.fujivibe.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TagSetCodecTest {
    @Test
    fun `a set round trips`() {
        assertEquals(setOf("mountains", "hard-light"), TagSetCodec.decode(TagSetCodec.encode(setOf("mountains", "hard-light"))))
    }

    @Test
    fun `nothing, blanks and spaces decode cleanly`() {
        assertEquals(emptySet<String>(), TagSetCodec.decode(null))
        assertEquals(emptySet<String>(), TagSetCodec.decode(""))
        assertEquals(setOf("a", "b"), TagSetCodec.decode(" a, ,b,"))
    }
}
