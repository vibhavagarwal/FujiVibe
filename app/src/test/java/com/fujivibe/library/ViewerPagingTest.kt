package com.fujivibe.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ViewerPagingTest {

    private val photos = listOf(10L, 20L, 30L).map { LibraryPhoto(it, "Charcoal_20261010_215300.jpg", it) }

    @Test
    fun `the viewer starts on the page of the photo that was tapped`() {
        assertEquals(0, startPage(photos, 10))
        assertEquals(2, startPage(photos, 30))
    }

    @Test
    fun `a photo that isn't in the list has no page`() {
        assertNull(startPage(photos, 99))
        assertNull(startPage(emptyList(), 10))
    }

    @Test
    fun `the position is counted from one`() {
        assertEquals("1 / 13", positionLabel(page = 0, count = 13))
        assertEquals("13 / 13", positionLabel(page = 12, count = 13))
    }

    @Test
    fun `a photo within the limit is decoded at full size`() {
        assertEquals(1, decodeSampleSize(longestEdge = 4080, maxEdge = 4096))
        assertEquals(1, decodeSampleSize(longestEdge = 4096, maxEdge = 4096))
    }

    @Test
    fun `a bigger photo is halved until it fits`() {
        assertEquals(2, decodeSampleSize(longestEdge = 8160, maxEdge = 4096))
        assertEquals(4, decodeSampleSize(longestEdge = 16320, maxEdge = 4096))
    }

    @Test
    fun `after a delete the viewer shows the next older photo`() {
        // Newest first, so the photo after the deleted one in the list is the older neighbour.
        assertEquals(20L, photoAfterDelete(photos, 10))
        assertEquals(30L, photoAfterDelete(photos, 20))
    }

    @Test
    fun `deleting the oldest photo falls back to the one before it`() {
        assertEquals(20L, photoAfterDelete(photos, 30))
    }

    @Test
    fun `deleting the only photo, or one that isn't listed, leaves nothing to show`() {
        assertNull(photoAfterDelete(photos.take(1), 10))
        assertNull(photoAfterDelete(photos, 99))
        assertNull(photoAfterDelete(emptyList(), 10))
    }
}
