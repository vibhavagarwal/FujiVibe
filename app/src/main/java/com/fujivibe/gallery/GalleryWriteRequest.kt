package com.fujivibe.gallery

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val MIME_TYPE = "image/jpeg"
private const val RELATIVE_PATH = "Pictures/FujiVibe/"

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
 * Builds the request for a Capture exported at [timestampMillis]. UTC rather than the device's
 * local time zone, so the display name is deterministic regardless of where this runs.
 */
fun galleryWriteRequest(timestampMillis: Long): GalleryWriteRequest {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(timestampMillis))

    return GalleryWriteRequest(
        displayName = "FujiVibe_$stamp.jpg",
        mimeType = MIME_TYPE,
        relativePath = RELATIVE_PATH,
    )
}
