package com.fujivibe.gallery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GalleryWriteRequestTest {

    @Test
    fun `request names the file FujiVibe-prefixed and jpg, image jpeg mime, under Pictures FujiVibe`() {
        val request = galleryWriteRequest(timestampMillis = 1_700_000_000_000L)

        assertEquals("FujiVibe_20231114_221320.jpg", request.displayName)
        assertEquals("image/jpeg", request.mimeType)
        assertEquals("Pictures/FujiVibe/", request.relativePath)
    }

    @Test
    fun `two requests built for different timestamps get different display names`() {
        val first = galleryWriteRequest(timestampMillis = 1_700_000_000_000L)
        val second = galleryWriteRequest(timestampMillis = 1_700_000_001_000L)

        assert(first.displayName != second.displayName)
    }
}
