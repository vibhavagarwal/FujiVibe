package com.fujivibe.recipe

import com.fujivibe.camera.FujiFilmSim
import org.json.JSONArray
import org.json.JSONObject

/** Reads the recipe set (`assets/recipes.json`): `{"recipes": [ ... ]}`; keys starting with `_` are comments. */
object RecipeJson {

    fun parse(text: String): List<Recipe> {
        val array = JSONObject(text).getJSONArray("recipes")
        return (0 until array.length()).map { recipe(array.getJSONObject(it)) }
    }

    private fun recipe(o: JSONObject): Recipe {
        val id = o.getString("id")
        val baseId = o.getString("baseSimulation")
        return Recipe(
            id = id,
            name = o.getString("name"),
            kind = when (o.getString("kind")) {
                "stock" -> RecipeKind.STOCK
                "community" -> RecipeKind.COMMUNITY
                "own" -> RecipeKind.OWN
                else -> throw IllegalArgumentException("Recipe $id: unknown kind '${o.getString("kind")}'")
            },
            baseSimulation = FujiFilmSim.fromId(baseId)
                ?: throw IllegalArgumentException("Recipe $id: unknown film simulation '$baseId'"),
            settings = settings(o.getJSONObject("settings")),
            shootingNotes = o.optJSONObject("shootingNotes")?.let {
                ShootingNotes(it.optString("iso"), it.optString("exposureCompensation"))
            } ?: ShootingNotes(),
            source = o.optJSONObject("source")?.let {
                RecipeSource(it.optString("author"), it.optString("publisher"), it.stringOrNull("url"))
            } ?: RecipeSource(),
            userDescription = o.stringOrNull("userDescription"),
            bestFor = o.optJSONObject("bestFor")?.let {
                BestFor(it.strings("subjects"), it.strings("light"), it.strings("mood"))
            } ?: BestFor(),
            avoid = o.strings("avoid"),
            recommendationText = o.optString("recommendationText"),
            confidence = o.optString("confidence"),
            notes = o.optString("notes"),
        )
    }

    private fun settings(o: JSONObject) = RecipeSettings(
        dynamicRange = o.stringOrNull("dynamicRange")?.let {
            when (it) {
                "auto" -> DynamicRange.AUTO
                "dr100" -> DynamicRange.DR100
                "dr200" -> DynamicRange.DR200
                "dr400" -> DynamicRange.DR400
                else -> throw IllegalArgumentException("Unknown dynamicRange '$it'")
            }
        },
        dRangePriority = o.stringOrNull("dRangePriority")?.let { enumOf<DRangePriority>("dRangePriority", it) },
        grain = o.optJSONObject("grain")?.let {
            Grain(
                enumOf<GrainStrength>("grain strength", it.getString("strength")),
                it.stringOrNull("size")?.let { size -> enumOf<GrainSize>("grain size", size) },
            )
        },
        colorChrome = o.stringOrNull("colorChrome")?.let { enumOf<Level>("colorChrome", it) },
        colorChromeFxBlue = o.stringOrNull("colorChromeFxBlue")?.let { enumOf<Level>("colorChromeFxBlue", it) },
        whiteBalance = o.optJSONObject("whiteBalance")?.let {
            WhiteBalance(
                mode = it.stringOrNull("mode")?.let { mode -> enumOf<WhiteBalanceMode>("white balance mode", mode) },
                kelvin = it.intOrNull("kelvin"),
                shiftR = it.intOrNull("shiftR"),
                shiftB = it.intOrNull("shiftB"),
            )
        },
        highlight = o.doubleOrNull("highlight"),
        shadow = o.doubleOrNull("shadow"),
        color = o.doubleOrNull("color"),
        sharpness = o.doubleOrNull("sharpness"),
        highIsoNr = o.intOrNull("highIsoNr"),
        clarity = o.doubleOrNull("clarity"),
        monochromaticColor = o.optJSONObject("monochromaticColor")?.let {
            MonochromaticColor(it.getInt("wc"), it.getInt("mg"))
        },
    )

    private inline fun <reified E : Enum<E>> enumOf(what: String, value: String): E =
        enumValues<E>().firstOrNull { it.name.equals(value.replace('-', '_'), ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown $what '$value'")

    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun JSONObject.doubleOrNull(key: String): Double? = if (isNull(key)) null else getDouble(key)

    private fun JSONObject.strings(key: String): List<String> {
        val array: JSONArray = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }
}
