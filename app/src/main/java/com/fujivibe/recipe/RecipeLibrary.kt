package com.fujivibe.recipe

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The recipes the app offers: the researched set that ships with it, plus any the user has pasted in. */
class RecipeLibrary(private val context: Context) {
    private val prefs = context.getSharedPreferences("recipes", Context.MODE_PRIVATE)

    private val shipped: List<Recipe> by lazy {
        RecipeJson.parse(context.assets.open("recipes.json").bufferedReader().use { it.readText() })
    }

    fun all(): List<Recipe> = shipped + own()

    fun find(id: String): Recipe? = all().firstOrNull { it.id == id }

    private fun own(): List<Recipe> {
        val array = JSONArray(prefs.getString(KEY_OWN, "[]"))
        return (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            RecipeTextParser.parse(o.getString("text"), o.getString("name")).recipe?.copy(id = o.getString("id"))
        }
    }

    /**
     * Reads [text] as a recipe and, if it is one, keeps it under [name] (or the name in the text).
     * Returns what the parser made of it, so a screen can show its warnings.
     */
    fun addFromText(text: String, name: String?): RecipeTextParser.Result {
        val result = RecipeTextParser.parse(text, name)
        val recipe = result.recipe ?: return result
        val taken = all().map { it.id }.toSet()
        var id = recipe.id
        var n = 2
        while (id in taken) id = "${recipe.id}-${n++}"
        val array = JSONArray(prefs.getString(KEY_OWN, "[]"))
        array.put(JSONObject().put("id", id).put("name", recipe.name).put("text", text))
        prefs.edit().putString(KEY_OWN, array.toString()).apply()
        return RecipeTextParser.Result(recipe.copy(id = id), result.warnings)
    }

    private companion object {
        const val KEY_OWN = "own"
    }
}
