package com.fujivibe.render.tools

import com.fujivibe.render.CubeLutParser
import com.fujivibe.render.FilmSimulation
import com.fujivibe.render.GrainRenderPipeline
import com.fujivibe.render.LutRenderPipeline
import com.fujivibe.render.PixelImage
import com.fujivibe.render.RenderSelection
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders a real photo through a [FilmSimulation] and writes an original-vs-rendered
 * side-by-side image, so a look can be tuned by eye without installing the app on a device.
 * Run via `./gradlew :render:previewFilmSimulation --args="<input> <output> [PANEL[,PANEL...]]"`
 * (defaults to `ORIGINAL,CLASSIC_NEG_PIXEL`). Reuses the app's own [LutRenderPipeline] and
 * [GrainRenderPipeline] rather than reimplementing either, so the preview matches what the app
 * actually renders, grain included.
 */
object PreviewFilmSimulation {

    private val LUT_DIRECTORIES = mapOf(
        FilmSimulation.CLASSIC_NEG_PIXEL to File("derived-luts"),
        FilmSimulation.NOSTALGIC_NEG_PIXEL to File("derived-luts"),
        FilmSimulation.KODACHROME_64 to File("derived-luts"),
    )

    fun toPixelImage(image: BufferedImage): PixelImage {
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        return PixelImage(image.width, image.height, pixels)
    }

    fun toBufferedImage(image: PixelImage): BufferedImage {
        val buffered = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        buffered.setRGB(0, 0, image.width, image.height, image.pixels, 0, image.width)
        return buffered
    }

    /**
     * Same pixels, but alpha-free (`TYPE_INT_RGB`) — camera photos are always opaque, and a
     * JPEG writer has no alpha channel to put an ARGB image's alpha into: `ImageIO.write`
     * returns `false` rather than throwing, so writing an ARGB image as JPEG silently produces
     * no output at all.
     */
    fun toOpaqueBufferedImage(image: PixelImage): BufferedImage {
        val buffered = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        buffered.setRGB(0, 0, image.width, image.height, image.pixels, 0, image.width)
        return buffered
    }

    /** [sideBySide], repeated across an ordered list of two or more same-height images. */
    fun sideBySideAll(images: List<PixelImage>): PixelImage {
        require(images.size >= 2) { "sideBySideAll needs at least 2 images, got ${images.size}" }
        return images.reduce(::sideBySide)
    }

    /** Places [left]'s rows before [right]'s rows, per row; both must share a height. */
    fun sideBySide(left: PixelImage, right: PixelImage): PixelImage {
        require(left.height == right.height) {
            "left and right must have the same height to sit side by side, got ${left.height} vs ${right.height}"
        }
        val width = left.width + right.width
        val pixels = IntArray(width * left.height)
        for (y in 0 until left.height) {
            System.arraycopy(left.pixels, y * left.width, pixels, y * width, left.width)
            System.arraycopy(right.pixels, y * right.width, pixels, y * width + left.width, right.width)
        }
        return PixelImage(width, left.height, pixels)
    }

    private fun renderPanel(original: PixelImage, panelName: String): PixelImage {
        if (panelName == "ORIGINAL") return original
        val filmSimulation = FilmSimulation.valueOf(panelName)
        val lutFile = File(LUT_DIRECTORIES.getValue(filmSimulation), filmSimulation.cubeResourceName)
        val pipeline = GrainRenderPipeline(LutRenderPipeline(lutLoader = { CubeLutParser.parse(lutFile.readText()) }))
        return runBlocking { pipeline.render(original, RenderSelection.Simulation(filmSimulation)) }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) {
            "Usage: previewFilmSimulation <input> <output> [PANEL[,PANEL...]] " +
                "-- each PANEL is ORIGINAL or a FilmSimulation name; default is ORIGINAL,CLASSIC_NEG_PIXEL"
        }
        val inputFile = File(args[0])
        val outputFile = File(args[1])
        val panelNames = args.getOrElse(2) { "ORIGINAL,CLASSIC_NEG_PIXEL" }.split(",")

        val original = toPixelImage(ImageIO.read(inputFile))
        val panels = panelNames.map { renderPanel(original, it) }

        val composite = sideBySideAll(panels)
        outputFile.parentFile?.mkdirs()
        val format = outputFile.extension.ifBlank { "png" }
        val wrote = ImageIO.write(toOpaqueBufferedImage(composite), format, outputFile)
        check(wrote) { "No ImageIO writer found for format \"$format\" (from ${outputFile.name})" }
        println("Wrote ${outputFile.path} (${composite.width}x${composite.height}, ${panelNames.joinToString(" | ")})")
    }
}
