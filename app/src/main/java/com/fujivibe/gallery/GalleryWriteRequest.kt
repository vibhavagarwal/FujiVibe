package com.fujivibe.gallery

import com.fujivibe.render.RenderSelection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val MIME_TYPE = "image/jpeg"
/** Where every Export is written, and where the Library reads from. */
const val EXPORT_RELATIVE_PATH = "Pictures/FujiVibe/"

/**
 * The shape of a gallery write, decoupled from Android's `ContentValues` so it can be asserted
 * in a pure JVM unit test without touching MediaStore itself — see the spec's Testing Decisions.
 */
data class GalleryWriteRequest(
    val displayName: String,
    val mimeType: String,
    val relativePath: String,
)

/**
 * Builds the request for a Capture exported with [selection] at [timestampMillis], named
 * `<look>_<datetime>.jpg` (e.g. `Charcoal_20231114_221320.jpg`) so the look is readable from the
 * file name, in the Library and in any other photo manager. UTC rather than the device's local
 * time zone, so the display name is deterministic regardless of where this runs.
 */
fun galleryWriteRequest(timestampMillis: Long, selection: RenderSelection): GalleryWriteRequest {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(timestampMillis))

    return GalleryWriteRequest(
        displayName = "${lookFileLabel(selection)}_$stamp.jpg",
        mimeType = MIME_TYPE,
        relativePath = EXPORT_RELATIVE_PATH,
    )
}
