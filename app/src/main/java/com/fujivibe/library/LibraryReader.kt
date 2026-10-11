package com.fujivibe.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.fujivibe.gallery.EXPORT_RELATIVE_PATH
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "MediaStoreLibraryReader"

/** Lists the photos FujiVibe has Exported. The reading counterpart of `GalleryWriter`. */
interface LibraryReader {

    /** Newest first. Only what the app is currently allowed to see; empty on any failure, never throws. */
    suspend fun photos(): List<LibraryPhoto>
}

/**
 * [LibraryReader] backed by [MediaStore]: no database and no second copy, so a photo deleted in
 * another app simply stops appearing.
 */
class MediaStoreLibraryReader(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LibraryReader {

    override suspend fun photos(): List<LibraryPhoto> = withContext(dispatcher) {
        try {
            val photos = mutableListOf<LibraryPhoto>()
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.DATE_ADDED,
                ),
                "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
                arrayOf(EXPORT_RELATIVE_PATH),
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    photos += LibraryPhoto(
                        id = cursor.getLong(idColumn),
                        displayName = cursor.getString(nameColumn),
                        dateAddedSeconds = cursor.getLong(addedColumn),
                    )
                }
            }
            photos.newestFirst()
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to list photos", e)
            emptyList()
        }
    }
}

/** The `content://` Uri of a [LibraryPhoto], for thumbnails and, later, the viewer. */
fun libraryPhotoUri(id: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
