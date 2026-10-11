package com.fujivibe.library

/**
 * Which page of the viewer's pager shows the photo [photoId]; null when it isn't in [photos] (it
 * was deleted, say), in which case there is nothing to show and the viewer should close.
 */
fun startPage(photos: List<LibraryPhoto>, photoId: Long): Int? =
    photos.indexOfFirst { it.id == photoId }.takeIf { it >= 0 }

/** "3 / 13": where the page is in the Library, counted from 1. */
fun positionLabel(page: Int, count: Int): String = "${page + 1} / $count"

/**
 * The power-of-two subsampling that brings a photo's [longestEdge] down to at most [maxEdge]
 * pixels (1 meaning full size). The viewer decodes the saved JPEG itself, so this only bites for
 * photos bigger than any this phone's camera produces.
 */
fun decodeSampleSize(longestEdge: Int, maxEdge: Int): Int {
    var sample = 1
    while (longestEdge / sample > maxEdge) sample *= 2
    return sample
}

/**
 * Which photo the viewer should move to once [deletedId] is gone: the next older one (the one that
 * takes its place in the newest-first list), else the one before it; null when it was the only
 * photo, or isn't in [photos], meaning there is nothing left to show.
 */
fun photoAfterDelete(photos: List<LibraryPhoto>, deletedId: Long): Long? {
    val index = photos.indexOfFirst { it.id == deletedId }
    if (index < 0) return null
    return (photos.getOrNull(index + 1) ?: photos.getOrNull(index - 1))?.id
}
