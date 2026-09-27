package com.fujivibe.gallery

import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileDescriptor

/** The Capture's camera details worth keeping on an Export; orientation is deliberately not one. */
private val CAMERA_TAGS = listOf(
    ExifInterface.TAG_DATETIME,
    ExifInterface.TAG_DATETIME_ORIGINAL,
    ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_OFFSET_TIME,
    ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
    ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
    ExifInterface.TAG_SUBSEC_TIME,
    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
    ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
    ExifInterface.TAG_MAKE,
    ExifInterface.TAG_MODEL,
    ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
    ExifInterface.TAG_EXPOSURE_TIME,
    ExifInterface.TAG_F_NUMBER,
    ExifInterface.TAG_FOCAL_LENGTH,
    ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
    ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
    ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
    ExifInterface.TAG_WHITE_BALANCE,
    ExifInterface.TAG_FLASH,
)

/**
 * Copies [source]'s camera details onto the JPEG open at [target]. The Export's pixels are
 * already upright, so its orientation is set to normal rather than copied.
 */
fun copyCameraMetadata(source: File, target: FileDescriptor) {
    val from = ExifInterface(source.path)
    ExifInterface(target).apply {
        for (tag in CAMERA_TAGS) from.getAttribute(tag)?.let { setAttribute(tag, it) }
        setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        setAttribute(ExifInterface.TAG_SOFTWARE, "FujiVibe")
        saveAttributes()
    }
}
