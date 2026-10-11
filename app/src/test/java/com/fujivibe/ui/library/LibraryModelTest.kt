package com.fujivibe.ui.library

import com.fujivibe.library.LibraryPhoto
import com.fujivibe.library.LibraryReader
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LibraryModelTest {

    private fun photo(id: Long) = LibraryPhoto(id, "Charcoal_20261010_215300.jpg", id)

    private class FakeReader(var photos: List<LibraryPhoto>) : LibraryReader {
        override suspend fun photos() = photos
    }

    @Test
    fun `the list is null until the first load`() {
        assertNull(LibraryModel(FakeReader(listOf(photo(1)))).photos)
    }

    @Test
    fun `refresh loads what the reader has`() = runBlocking {
        val model = LibraryModel(FakeReader(listOf(photo(2), photo(1))))

        model.refresh()

        assertEquals(listOf(photo(2), photo(1)), model.photos)
    }

    @Test
    fun `remove drops just that photo`() = runBlocking {
        val model = LibraryModel(FakeReader(listOf(photo(3), photo(2), photo(1))))
        model.refresh()

        model.remove(2)

        assertEquals(listOf(photo(3), photo(1)), model.photos)
    }

    @Test
    fun `removing before the first load, or an unknown photo, changes nothing`() = runBlocking {
        val model = LibraryModel(FakeReader(listOf(photo(1))))

        model.remove(1)
        assertNull(model.photos)

        model.refresh()
        model.remove(99)
        assertEquals(listOf(photo(1)), model.photos)
    }

    @Test
    fun `a later refresh replaces the list, so a delete that failed to stick comes back`() = runBlocking {
        val reader = FakeReader(listOf(photo(2), photo(1)))
        val model = LibraryModel(reader)
        model.refresh()
        model.remove(2)

        model.refresh()

        assertEquals(listOf(photo(2), photo(1)), model.photos)
    }
}
