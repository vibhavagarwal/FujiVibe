package com.fujivibe.ui.library

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fujivibe.library.LibraryPhoto
import com.fujivibe.library.LibraryReader

/**
 * The Library's photo list, held above the screens so the grid and the viewer see the same one:
 * swiping in the viewer walks the very list the grid is showing. [photos] is null until the first
 * load finishes.
 */
@Stable
class LibraryModel(private val reader: LibraryReader) {

    var photos by mutableStateOf<List<LibraryPhoto>?>(null)
        private set

    suspend fun refresh() {
        photos = reader.photos()
    }

    /** Drops a photo that has just been deleted, without waiting for a reload. */
    fun remove(photoId: Long) {
        photos = photos?.filterNot { it.id == photoId }
    }
}
