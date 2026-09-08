package com.fujivibe.render

/**
 * A resolution-agnostic bitmap: ARGB_8888 pixels (0xAARRGGBB, row-major), decoupled from any
 * platform bitmap type so the render pipeline can serve both a downscaled preview and a
 * full-resolution export through the same code path.
 *
 * The constructor defensively copies its input, so mutating the array you passed in afterward
 * never affects this instance. [pixels] itself is still exposed by reference (a full-resolution
 * copy on every read would be too costly) — callers must treat it as read-only.
 */
class PixelImage(width: Int, height: Int, pixels: IntArray) {

    val width: Int = width
    val height: Int = height
    val pixels: IntArray = pixels.copyOf()

    init {
        require(width > 0 && height > 0) { "width and height must be positive, got ${width}x$height" }
        require(this.pixels.size == width * height) {
            "Expected ${width * height} pixels for ${width}x$height, got ${this.pixels.size}"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is PixelImage && width == other.width && height == other.height && pixels.contentEquals(other.pixels)

    override fun hashCode(): Int = 31 * (31 * width + height) + pixels.contentHashCode()
}
