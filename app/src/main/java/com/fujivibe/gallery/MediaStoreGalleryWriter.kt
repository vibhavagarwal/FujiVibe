package com.fujivibe.gallery

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Log
import com.fujivibe.bitmap.toBitmap
import com.fujivibe.render.PixelImage
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "MediaStoreGalleryWriter"

/**
 * [GalleryWriter] backed by [MediaStore], scoped-storage only (minSdk 29) per the spec — no
 * legacy direct-file-path writes. Inserts pending, writes pixels, then clears pending only once
 * the write fully succeeds, so a crash or exception mid-write can't leave a corrupt file visible
 * in the gallery.
 */
class MediaStoreGalleryWriter(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GalleryWriter {

    override suspend fun save(image: PixelImage, metadataFrom: File?): Boolean = withContext(dispatcher) {
        val request = galleryWriteRequest(System.currentTimeMillis())
        val resolver = context.contentResolver

        val uri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, request.displayName)
                put(MediaStore.Images.Media.MIME_TYPE, request.mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, request.relativePath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            },
        )

        if (uri == null) {
            Log.e(TAG, "MediaStore insert returned no uri")
            return@withContext false
        }

        try {
            val wrote = resolver.openOutputStream(uri)?.use { out ->
                val bitmap = image.toBitmap()
                val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                bitmap.recycle()
                compressed
            } ?: false

            if (!wrote) {
                resolver.delete(uri, null, null)
                return@withContext false
            }

            // Best effort: a photo without its camera details is still worth keeping.
            if (metadataFrom != null) {
                try {
                    resolver.openFileDescriptor(uri, "rw")?.use { copyCameraMetadata(metadataFrom, it.fileDescriptor) }
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't copy camera metadata onto the export", e)
                }
            }

            // Only IS_PENDING=0 actually makes the file visible in the gallery, so a request
            // that updated zero rows (e.g. the pending row was reclaimed mid-write) must be
            // treated the same as a failed write, not a confirmed success.
            val clearedPending = resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )

            if (clearedPending <= 0) {
                Log.e(TAG, "MediaStore update to clear IS_PENDING affected no rows")
                resolver.delete(uri, null, null)
                return@withContext false
            }

            true
        } catch (e: CancellationException) {
            resolver.delete(uri, null, null)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gallery write failed", e)
            resolver.delete(uri, null, null)
            false
        }
    }
}
