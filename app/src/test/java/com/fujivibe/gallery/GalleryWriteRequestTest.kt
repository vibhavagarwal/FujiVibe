package com.fujivibe.gallery

import com.fujivibe.render.FilmSimulation
import com.fujivibe.render.RenderSelection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class GalleryWriteRequestTest {

    private val charcoal = RenderSelection.Simulation(FilmSimulation.CHARCOAL)

    @Test
    fun `request names the file look_datetime and jpg, image jpeg mime, under Pictures FujiVibe`() {
        val request = galleryWriteRequest(timestampMillis = 1_700_000_000_000L, selection = charcoal)

        assertEquals("Charcoal_20231114_221320.jpg", request.displayName)
        assertEquals("image/jpeg", request.mimeType)
        assertEquals("Pictures/FujiVibe/", request.relativePath)
    }

    @Test
    fun `the name starts with whichever look was exported`() {
        val timestamp = 1_700_000_000_000L

        assertEquals(
            "Original_20231114_221320.jpg",
            galleryWriteRequest(timestamp, RenderSelection.Original).displayName,
        )
        assertEquals(
            "Koda64Pixel_20231114_221320.jpg",
            galleryWriteRequest(timestamp, RenderSelection.Simulation(FilmSimulation.KODA64_PIXEL)).displayName,
        )
    }

    @Test
    fun `two requests built for different timestamps get different display names`() {
        val first = galleryWriteRequest(timestampMillis = 1_700_000_000_000L, selection = charcoal)
        val second = galleryWriteRequest(timestampMillis = 1_700_000_001_000L, selection = charcoal)

        assertNotEquals(first.displayName, second.displayName)
    }

    @Test
    fun `two looks exported in the same second still get different display names`() {
        val timestamp = 1_700_000_000_000L

        assertNotEquals(
            galleryWriteRequest(timestamp, charcoal).displayName,
            galleryWriteRequest(timestamp, RenderSelection.Original).displayName,
        )
    }
}
