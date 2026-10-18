package com.fujivibe.recipe

import com.fujivibe.camera.FujiFilmSim

/**
 * Turns a recipe pasted as text into a [Recipe], for the usual "Setting: value" lists such as
 * Fuji X Weekly's. It never guesses: a line or value it cannot read is reported in [Result.warnings]
 * and left unset, and a missing film simulation means no recipe at all.
 */
object RecipeTextParser {

    class Result(val recipe: Recipe?, val warnings: List<String>)

    private enum class Key {
        FILM, DYNAMIC_RANGE, D_RANGE_PRIORITY, GRAIN, COLOR_CHROME, COLOR_CHROME_FX_BLUE,
        WHITE_BALANCE, HIGHLIGHT, SHADOW, COLOR, SHARPNESS, NOISE_REDUCTION, CLARITY,
        MONOCHROMATIC_COLOR, ISO, EXPOSURE_COMPENSATION, IGNORED,
    }

    /** Every label a recipe may use, lower case, mapped to what it means. Longer labels must win over their prefixes. */
    private val labels: List<Pair<String, Key>> = listOf(
        "film simulation" to Key.FILM, "film sim" to Key.FILM,
        "d range priority" to Key.D_RANGE_PRIORITY, "d-range priority" to Key.D_RANGE_PRIORITY,
        "dynamic range priority" to Key.D_RANGE_PRIORITY,
        "dynamic range" to Key.DYNAMIC_RANGE,
        "grain effect" to Key.GRAIN, "grain" to Key.GRAIN,
        "color chrome fx blue" to Key.COLOR_CHROME_FX_BLUE, "colour chrome fx blue" to Key.COLOR_CHROME_FX_BLUE,
        "color chrome effect blue" to Key.COLOR_CHROME_FX_BLUE, "colour chrome effect blue" to Key.COLOR_CHROME_FX_BLUE,
        "color chrome blue" to Key.COLOR_CHROME_FX_BLUE,
        "color chrome effect" to Key.COLOR_CHROME, "colour chrome effect" to Key.COLOR_CHROME,
        "color chrome" to Key.COLOR_CHROME, "colour chrome" to Key.COLOR_CHROME,
        "white balance" to Key.WHITE_BALANCE,
        "highlight tone" to Key.HIGHLIGHT, "highlights" to Key.HIGHLIGHT, "highlight" to Key.HIGHLIGHT,
        "shadow tone" to Key.SHADOW, "shadows" to Key.SHADOW, "shadow" to Key.SHADOW,
        "monochromatic color" to Key.MONOCHROMATIC_COLOR, "monochromatic colour" to Key.MONOCHROMATIC_COLOR,
        "color" to Key.COLOR, "colour" to Key.COLOR,
        "sharpness" to Key.SHARPNESS,
        "high iso noise reduction" to Key.NOISE_REDUCTION, "high iso nr" to Key.NOISE_REDUCTION,
        "noise reduction" to Key.NOISE_REDUCTION,
        "clarity" to Key.CLARITY,
        "iso" to Key.ISO,
        "exposure compensation" to Key.EXPOSURE_COMPENSATION,
        "smooth skin effect" to Key.IGNORED, "lens modulation optimizer" to Key.IGNORED, "color space" to Key.IGNORED,
        "colour space" to Key.IGNORED,
    ).sortedByDescending { it.first.length }

    private val labelRegex = Regex(
        "(?<![A-Za-z])(" + labels.joinToString("|") { Regex.escape(it.first) } + ")\\s*:",
        RegexOption.IGNORE_CASE,
    )

    /** [nameHint] names the recipe; without one the first line that has no label is used. */
    fun parse(text: String, nameHint: String? = null): Result {
        val warnings = mutableListOf<String>()
        var name = nameHint?.trim()?.takeIf { it.isNotEmpty() }
        val values = linkedMapOf<Key, String>()

        for (rawLine in text.replace('−', '-').lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val matches = labelRegex.findAll(line).toList()
            if (matches.isEmpty()) {
                if (name == null && line.length <= 60) name = line.trim('#', '*', ' ') else warnings += "Could not read the line: \"$line\""
                continue
            }
            if (matches.first().range.first > 0) {
                val before = line.substring(0, matches.first().range.first).trim()
                if (name == null && before.length <= 60) name = before.trim('#', '*', ' ', '-') else if (before.isNotEmpty()) warnings += "Ignored text before \"${matches.first().value}\": \"$before\""
            }
            matches.forEachIndexed { i, m ->
                val end = if (i + 1 < matches.size) matches[i + 1].range.first else line.length
                val key = labels.first { it.first.equals(m.groupValues[1], ignoreCase = true) }.second
                values[key] = line.substring(m.range.last + 1, end).trim().trimEnd(',', ';')
            }
        }

        val filmText = values[Key.FILM]
        if (filmText == null) {
            warnings += "No film simulation found, so there is nothing to develop with."
            return Result(null, warnings)
        }
        val film = parseFilmSimulation(filmText)
        if (film == null) {
            warnings += "Do not know the film simulation \"$filmText\"."
            return Result(null, warnings)
        }
        val recipeName = name ?: film.label

        fun <T> read(key: Key, what: String, parse: (String) -> T?): T? {
            val raw = values[key] ?: return null
            return parse(raw) ?: run { warnings += "Could not read $what: \"$raw\" (left unset)"; null }
        }

        val settings = RecipeSettings(
            dynamicRange = read(Key.DYNAMIC_RANGE, "Dynamic Range", ::parseDynamicRange),
            dRangePriority = read(Key.D_RANGE_PRIORITY, "D Range Priority", ::parseDRangePriority),
            grain = read(Key.GRAIN, "Grain Effect", ::parseGrain),
            colorChrome = read(Key.COLOR_CHROME, "Color Chrome Effect", ::parseLevel),
            colorChromeFxBlue = read(Key.COLOR_CHROME_FX_BLUE, "Color Chrome FX Blue", ::parseLevel),
            whiteBalance = read(Key.WHITE_BALANCE, "White Balance", ::parseWhiteBalance),
            highlight = read(Key.HIGHLIGHT, "Highlight", ::parseNumber),
            shadow = read(Key.SHADOW, "Shadow", ::parseNumber),
            color = read(Key.COLOR, "Color", ::parseNumber),
            sharpness = read(Key.SHARPNESS, "Sharpness", ::parseNumber),
            highIsoNr = read(Key.NOISE_REDUCTION, "Noise Reduction", ::parseNumber)?.toInt(),
            clarity = read(Key.CLARITY, "Clarity", ::parseNumber),
            monochromaticColor = read(Key.MONOCHROMATIC_COLOR, "Monochromatic Color", ::parseMonochromaticColor),
        )
        val recipe = Recipe(
            id = slug(recipeName),
            name = recipeName,
            kind = RecipeKind.OWN,
            baseSimulation = film,
            settings = settings,
            shootingNotes = ShootingNotes(
                iso = values[Key.ISO].orEmpty(),
                exposureCompensation = values[Key.EXPOSURE_COMPENSATION].orEmpty(),
            ),
            notes = "Added from pasted text.",
        )
        return Result(recipe, warnings)
    }

    private fun slug(name: String) = name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "recipe" }

    private fun parseFilmSimulation(text: String): FujiFilmSim? {
        val key = text.lowercase().replace(Regex("[^a-z+]"), "")
            .replace("negative", "neg").replace("standard", "std").replace("yellow", "ye")
            .replace("+ye", "ye").replace("+r", "r").replace("+g", "g")
        return when (key) {
            "provia", "proviastd", "proviastandard" -> FujiFilmSim.PROVIA
            "velvia", "velviavivid" -> FujiFilmSim.VELVIA
            "astia", "astiasoft" -> FujiFilmSim.ASTIA
            "proneghi" -> FujiFilmSim.PRO_NEG_HI
            "pronegstd" -> FujiFilmSim.PRO_NEG_STD
            "classicchrome" -> FujiFilmSim.CLASSIC_CHROME
            "classicneg" -> FujiFilmSim.CLASSIC_NEG
            "nostalgicneg" -> FujiFilmSim.NOSTALGIC_NEG
            "realaace" -> FujiFilmSim.REALA_ACE
            "eterna", "eternacinema" -> FujiFilmSim.ETERNA
            "eternableachbypass" -> FujiFilmSim.ETERNA_BLEACH
            "acros" -> FujiFilmSim.ACROS
            "acrosye" -> FujiFilmSim.ACROS_YE
            "acrosr" -> FujiFilmSim.ACROS_R
            "acrosg" -> FujiFilmSim.ACROS_G
            "monochrome" -> FujiFilmSim.MONOCHROME
            "monochromeye" -> FujiFilmSim.MONOCHROME_YE
            "monochromer" -> FujiFilmSim.MONOCHROME_R
            "monochromeg" -> FujiFilmSim.MONOCHROME_G
            "sepia" -> FujiFilmSim.SEPIA
            else -> null
        }
    }

    private fun parseDynamicRange(text: String): DynamicRange? {
        val t = text.lowercase().replace(Regex("[^a-z0-9]"), "")
        return when {
            t.contains("auto") -> DynamicRange.AUTO
            t.contains("400") -> DynamicRange.DR400
            t.contains("200") -> DynamicRange.DR200
            t.contains("100") -> DynamicRange.DR100
            else -> null
        }
    }

    private fun parseDRangePriority(text: String): DRangePriority? = when (text.trim().lowercase()) {
        "off" -> DRangePriority.OFF
        "auto" -> DRangePriority.AUTO
        "weak" -> DRangePriority.WEAK
        "strong" -> DRangePriority.STRONG
        else -> null
    }

    private fun parseLevel(text: String): Level? = when (text.trim().lowercase()) {
        "off" -> Level.OFF
        "weak" -> Level.WEAK
        "strong" -> Level.STRONG
        else -> null
    }

    private fun parseGrain(text: String): Grain? {
        val t = text.lowercase()
        val strength = when {
            t.contains("off") -> return Grain(GrainStrength.OFF)
            t.contains("weak") -> GrainStrength.WEAK
            t.contains("strong") -> GrainStrength.STRONG
            else -> return null
        }
        val size = when {
            t.contains("small") -> GrainSize.SMALL
            t.contains("large") -> GrainSize.LARGE
            else -> return null
        }
        return Grain(strength, size)
    }

    private fun parseNumber(text: String): Double? =
        Regex("^[+-]?\\d+(?:\\.\\d+)?").find(text.trim())?.value?.toDoubleOrNull()

    private fun parseWhiteBalance(text: String): WhiteBalance? {
        val t = text.lowercase()
        val kelvin = Regex("(\\d{4,5})\\s*k").find(t)?.groupValues?.get(1)?.toInt()
        val mode = when {
            kelvin != null -> WhiteBalanceMode.KELVIN
            t.contains("auto") && t.contains("white priority") -> WhiteBalanceMode.AUTO_WHITE_PRIORITY
            t.contains("auto") && t.contains("ambience priority") -> WhiteBalanceMode.AUTO_AMBIENCE_PRIORITY
            t.contains("auto") -> WhiteBalanceMode.AUTO
            t.contains("fluorescent 1") -> WhiteBalanceMode.FLUORESCENT_1
            t.contains("fluorescent 2") -> WhiteBalanceMode.FLUORESCENT_2
            t.contains("fluorescent 3") -> WhiteBalanceMode.FLUORESCENT_3
            t.contains("daylight") -> WhiteBalanceMode.DAYLIGHT
            t.contains("shade") -> WhiteBalanceMode.SHADE
            t.contains("incandescent") || t.contains("tungsten") -> WhiteBalanceMode.INCANDESCENT
            else -> null
        }
        // "+2 Red & -4 Blue", "R +2, B -4" and "R:+2 B:-4" all name the same two numbers.
        val r = Regex("([+-]?\\d+)\\s*red").find(t) ?: Regex("\\br\\s*[:=]?\\s*([+-]?\\d+)").find(t)
        val b = Regex("([+-]?\\d+)\\s*blue").find(t) ?: Regex("\\bb\\s*[:=]?\\s*([+-]?\\d+)").find(t)
        if (mode == null && r == null && b == null) return null
        return WhiteBalance(mode, kelvin, r?.groupValues?.get(1)?.toInt(), b?.groupValues?.get(1)?.toInt())
    }

    private fun parseMonochromaticColor(text: String): MonochromaticColor? {
        val wc = Regex("wc\\s*[:=]?\\s*([+-]?\\d+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toInt()
        val mg = Regex("mg\\s*[:=]?\\s*([+-]?\\d+)", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toInt()
        return if (wc != null && mg != null) MonochromaticColor(wc, mg) else null
    }
}
