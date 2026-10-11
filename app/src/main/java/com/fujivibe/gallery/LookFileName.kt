package com.fujivibe.gallery

import com.fujivibe.render.FilmSimulation
import com.fujivibe.render.RenderSelection

private const val ORIGINAL_LABEL = "Original"

/**
 * The look as it appears at the front of a saved file's name (`Charcoal_20231114_221320.jpg`):
 * its display name with everything but letters and digits dropped, so "Classic Neg. (Pixel)"
 * becomes `ClassicNegPixel`. Derived from the display name rather than listed, so a new look gets
 * a label for free.
 */
fun lookFileLabel(selection: RenderSelection): String = when (selection) {
    RenderSelection.Original -> ORIGINAL_LABEL
    is RenderSelection.Simulation -> selection.filmSimulation.fileLabel()
}

/**
 * The inverse of [lookFileLabel]: which look a saved file was exported with, read from the start
 * of its [displayName]; null when it doesn't begin with a look's label and an underscore, as with
 * the older `FujiVibe_<datetime>.jpg` names (which never recorded the look).
 */
fun lookFromFileName(displayName: String): RenderSelection? {
    val label = displayName.substringBefore('_', missingDelimiterValue = "")
    if (label.isEmpty()) return null
    if (label == ORIGINAL_LABEL) return RenderSelection.Original
    return FilmSimulation.entries.firstOrNull { it.fileLabel() == label }?.let { RenderSelection.Simulation(it) }
}

private fun FilmSimulation.fileLabel(): String = displayName.filter { it.isLetterOrDigit() }
