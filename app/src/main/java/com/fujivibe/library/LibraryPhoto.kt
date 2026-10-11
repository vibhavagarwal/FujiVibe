package com.fujivibe.library

import android.Manifest
import com.fujivibe.gallery.lookFromFileName
import com.fujivibe.render.RenderSelection
import com.fujivibe.review.displayLabel
import com.fujivibe.review.shortLabel

/**
 * One photo FujiVibe has Exported, as the Library lists it: its MediaStore [id] (turned into a
 * `content://` Uri only at the Android edge, so this stays testable on the plain JVM), its file
 * name, and when it was added.
 */
data class LibraryPhoto(
    val id: Long,
    val displayName: String,
    val dateAddedSeconds: Long,
) {
    /** The look it was Exported with, read from its name; null for older `FujiVibe_*` photos. */
    val look: RenderSelection? get() = lookFromFileName(displayName)

    val lookLabel: String? get() = look?.displayLabel()

    /** The look as an acronym (e.g. "CN"), for places too small for its name. */
    val lookShortLabel: String? get() = look?.shortLabel()
}

/** Latest first. Photos added in the same second fall back to the later row, so the order is stable. */
fun List<LibraryPhoto>.newestFirst(): List<LibraryPhoto> =
    sortedWith(compareByDescending<LibraryPhoto> { it.dateAddedSeconds }.thenByDescending { it.id })

/**
 * The permission needed to list photos FujiVibe didn't itself last write. Android lets an app see
 * only the media it currently owns without one, and a photo changes owner when another app (such as
 * Google Photos) takes it over, so most older Exports are invisible until the user allows this.
 */
fun libraryReadPermission(sdkInt: Int): String =
    if (sdkInt >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
