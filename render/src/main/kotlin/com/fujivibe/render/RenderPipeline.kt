package com.fujivibe.render

/**
 * The primary seam: source [PixelImage] plus a [RenderSelection] in, rendered [PixelImage] out.
 * The same call serves both a downscaled Review preview and a full-resolution Export.
 */
interface RenderPipeline {
    suspend fun render(source: PixelImage, selection: RenderSelection): PixelImage
}
