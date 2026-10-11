package com.fujivibe.library

import com.fujivibe.render.FilmSimulation
import com.fujivibe.render.RenderSelection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LibraryPhotoTest {

    private fun photo(id: Long, name: String = "Charcoal_20261010_215300.jpg", added: Long = 1_000L) =
        LibraryPhoto(id = id, displayName = name, dateAddedSeconds = added)

    @Test
    fun `newest first orders by date added, latest at the top`() {
        val older = photo(1, added = 100)
        val newer = photo(2, added = 200)
        val newest = photo(3, added = 300)

        assertEquals(listOf(newest, newer, older), listOf(older, newest, newer).newestFirst())
    }

    @Test
    fun `photos added in the same second keep a stable order, later row first`() {
        val first = photo(10, added = 500)
        val second = photo(11, added = 500)

        assertEquals(listOf(second, first), listOf(first, second).newestFirst())
        assertEquals(listOf(second, first), listOf(second, first).newestFirst())
    }

    @Test
    fun `an empty list stays empty`() {
        assertEquals(emptyList<LibraryPhoto>(), emptyList<LibraryPhoto>().newestFirst())
    }

    @Test
    fun `the look is read from the file name`() {
        assertEquals(
            RenderSelection.Simulation(FilmSimulation.CHARCOAL),
            photo(1, "Charcoal_20261010_215300.jpg").look,
        )
        assertEquals(RenderSelection.Original, photo(1, "Original_20261010_215300.jpg").look)
    }

    @Test
    fun `an older FujiVibe-named photo has no look and no label`() {
        val older = photo(1, "FujiVibe_20260927_213536.jpg")

        assertNull(older.look)
        assertNull(older.lookLabel)
    }

    @Test
    fun `the label is the look's display name`() {
        assertEquals("Charcoal", photo(1, "Charcoal_20261010_215300.jpg").lookLabel)
        assertEquals("Koda64 (Pixel)", photo(1, "Koda64Pixel_20261010_215300.jpg").lookLabel)
        assertEquals("Original", photo(1, "Original_20261010_215300.jpg").lookLabel)
    }

    @Test
    fun `the short label is the look's acronym`() {
        assertEquals("CN", photo(1, "ClassicNegPixel_20261010_215300.jpg").lookShortLabel)
        assertEquals("NN", photo(1, "NostalgicNegPixel_20261010_215300.jpg").lookShortLabel)
        assertEquals("K", photo(1, "Koda64Pixel_20261010_215300.jpg").lookShortLabel)
        assertEquals("CH", photo(1, "Charcoal_20261010_215300.jpg").lookShortLabel)
        assertEquals("O", photo(1, "Original_20261010_215300.jpg").lookShortLabel)
        assertNull(photo(1, "FujiVibe_20260927_213536.jpg").lookShortLabel)
    }

    @Test
    fun `every look has its own acronym, so thumbnails can't be confused`() {
        val shortNames = listOf("O") + FilmSimulation.entries.map { it.shortName }

        assertEquals(shortNames.size, shortNames.toSet().size)
    }

    @Test
    fun `reading other apps' saved photos needs the permission that matches the Android version`() {
        assertEquals("android.permission.READ_MEDIA_IMAGES", libraryReadPermission(sdkInt = 33))
        assertEquals("android.permission.READ_MEDIA_IMAGES", libraryReadPermission(sdkInt = 37))
        assertEquals("android.permission.READ_EXTERNAL_STORAGE", libraryReadPermission(sdkInt = 32))
        assertEquals("android.permission.READ_EXTERNAL_STORAGE", libraryReadPermission(sdkInt = 29))
    }
}
