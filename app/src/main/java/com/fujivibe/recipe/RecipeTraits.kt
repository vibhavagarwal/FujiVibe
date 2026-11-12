package com.fujivibe.recipe

import kotlin.math.abs
import kotlin.math.roundToInt

/** Short words for a recipe's character, for a list row: black and white, its mood tags, grain. */
fun Recipe.characterChips(): List<String> {
    val chips = mutableListOf<String>()
    if (baseSimulation.isMonochrome) chips += "B&W"
    chips += bestFor.mood.take(3).map { tag -> tag.replace('-', ' ').replaceFirstChar { it.uppercase() } }
    val grain = settings.grain
    if (grain != null && grain.strength != GrainStrength.OFF) chips += "Grain"
    return chips
}

/** The recipe's settings as label and value, in the order the camera's menu shows them; only the ones it sets. */
fun Recipe.settingLines(): List<Pair<String, String>> {
    val s = settings
    val lines = mutableListOf<Pair<String, String>>()
    lines += "Film simulation" to baseSimulation.label
    s.dynamicRange?.let { lines += "Dynamic range" to if (it == DynamicRange.AUTO) "Auto" else "DR${it.percent()}" }
    s.dRangePriority?.let { lines += "D Range Priority" to it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
    s.grain?.let { g ->
        lines += "Grain" to when (g.strength) {
            GrainStrength.OFF -> "Off"
            else -> g.strength.name.lowercase().replaceFirstChar { it.uppercase() } +
                (g.size?.let { ", " + it.name.lowercase() } ?: "")
        }
    }
    s.colorChrome?.let { lines += "Color Chrome Effect" to it.label() }
    s.colorChromeFxBlue?.let { lines += "Color Chrome FX Blue" to it.label() }
    s.whiteBalance?.let { wb ->
        val mode = when (wb.mode) {
            null -> null
            WhiteBalanceMode.KELVIN -> "${wb.kelvin ?: "?"} K"
            else -> wb.mode.label()
        }
        val shifts = if (wb.shiftR != null || wb.shiftB != null) "R ${signed((wb.shiftR ?: 0).toDouble())}, B ${signed((wb.shiftB ?: 0).toDouble())}" else null
        lines += "White balance" to listOfNotNull(mode, shifts).joinToString(", ").ifEmpty { "Camera's own" }
    }
    s.monochromaticColor?.let { lines += "B&W tint" to "warm-cool ${signed(it.wc.toDouble())}, magenta-green ${signed(it.mg.toDouble())}" }
    s.highlight?.let { lines += "Highlight tone" to signed(it) }
    s.shadow?.let { lines += "Shadow tone" to signed(it) }
    s.color?.let { lines += "Color" to signed(it) }
    s.sharpness?.let { lines += "Sharpness" to signed(it) }
    s.highIsoNr?.let { lines += "High ISO NR" to signed(it.toDouble()) }
    if (s.clarity != null && s.clarity != 0.0) lines += "Clarity" to "0 written (recipe: ${signed(s.clarity)})"
    else if (s.clarity != null) lines += "Clarity" to "0"
    return lines
}

private fun Level.label() = when (this) {
    Level.OFF -> "Off"
    Level.WEAK -> "Weak"
    Level.STRONG -> "Strong"
}

/** +2, 0, -1, +0.5: a step value as the camera's menu writes it. */
internal fun signed(v: Double): String {
    val whole = v.roundToInt()
    val text = if (abs(v - whole) < 1e-9) whole.toString() else v.toString()
    return if (v > 0) "+$text" else text
}

/**
 * Which recipes the picker shows: all of them when nothing is chosen. Within one group (say two subjects)
 * a recipe needs any of them; across groups it needs all.
 */
data class RecipeFilter(
    /** true: black and white only; false: colour only; null: both. */
    val monochrome: Boolean? = null,
    val subjects: Set<String> = emptySet(),
    val light: Set<String> = emptySet(),
    val mood: Set<String> = emptySet(),
) {
    val count: Int get() = (if (monochrome != null) 1 else 0) + subjects.size + light.size + mood.size

    fun matches(r: Recipe): Boolean =
        (monochrome == null || r.baseSimulation.isMonochrome == monochrome) &&
            (subjects.isEmpty() || r.bestFor.subjects.any { it in subjects }) &&
            (light.isEmpty() || r.bestFor.light.any { it in light }) &&
            (mood.isEmpty() || r.bestFor.mood.any { it in mood })
}

/** The distinct tags the recipes use for one group, most used first, so the common ones lead the filter row. */
fun List<Recipe>.tagsBy(group: (Recipe) -> List<String>): List<String> =
    flatMap(group).groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key }

/** A tag as a person reads it: "autumn-leaves" is "Autumn leaves". */
fun tagLabel(tag: String): String = tag.replace('-', ' ').replaceFirstChar { it.uppercase() }

/** How much a recipe leans on a diffusion filter now that its negative Clarity is never written. */
enum class FilterNeed { NONE, USE, STRONGER }

fun Recipe.filterNeed(): FilterNeed {
    val c = settings.clarity ?: return FilterNeed.NONE
    return when {
        c >= 0 -> FilterNeed.NONE
        c > -3 -> FilterNeed.USE
        else -> FilterNeed.STRONGER
    }
}

/** The line on a slot row: always there, so the rows can be scanned for the ones that need a filter. */
fun Recipe.filterLine(): String = "Filter: " + when (filterNeed()) {
    FilterNeed.NONE -> "not needed"
    FilterNeed.USE -> "use a filter"
    FilterNeed.STRONGER -> "use a filter (stronger)"
}

/** "3 of 6 recipes use a filter."; null when none are chosen. */
fun filterSummary(recipes: Collection<Recipe>): String? {
    if (recipes.isEmpty()) return null
    val n = recipes.count { it.filterNeed() != FilterNeed.NONE }
    return "$n of ${recipes.size} recipe${if (recipes.size == 1) "" else "s"} use a filter."
}

/** A sentence about the recipe's Clarity, which is never written because it slows saving; null when it has none to speak of. */
fun Recipe.clarityNote(): String? {
    val c = settings.clarity ?: return null
    return when (filterNeed()) {
        FilterNeed.NONE ->
            if (c == 0.0) null
            else "The recipe has Clarity ${signed(c)}, which is not written because it slows saving. Expect a little less bite; add it in X RAW Studio if you want it."
        FilterNeed.USE ->
            "The recipe has Clarity ${signed(c)}, which is not written because it slows saving. A diffusion filter such as a Black Pro-Mist or CineBloom gives a similar soft glow. A light one (1/8 or 5%) is a good start; go stronger if you like it dreamier."
        FilterNeed.STRONGER ->
            "The recipe has Clarity ${signed(c)}, which is not written because it slows saving. This one is softer than most, so a diffusion filter such as a Black Pro-Mist or CineBloom helps a lot. Try a medium one (1/4 or 10%) and go stronger if you like it dreamier."
    }
}
