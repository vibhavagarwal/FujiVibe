package com.fujivibe.gallery

import com.fujivibe.render.PixelImage
import java.io.File

/**
 * The secondary seam: writes a rendered [PixelImage] into the system gallery. Thinner than
 * [com.fujivibe.render.RenderPipeline] since this crosses into OS-managed scoped storage
 * (`Pictures/FujiVibe/` via MediaStore) rather than pure in-memory computation.
 */
interface GalleryWriter {

    /**
     * Returns true on a confirmed successful save, false on failure — never throws — so a caller
     * can leave the temp Capture in place and let the user retry rather than losing the shot.
     * Camera metadata (date, ISO, shutter, camera model) is copied from [metadataFrom] when given.
     */
    suspend fun save(image: PixelImage, metadataFrom: File? = null): Boolean
}
