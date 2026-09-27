package com.fujivibe.viewfinder

import android.content.Context

/**
 * The Viewfinder's set-once toggles (grid, level line, timer, aspect ratio), remembered across
 * app launches. Zoom, ISO and shutter are deliberately not stored: they reset on every bind.
 */
class ViewfinderSettings(context: Context) {
    private val prefs = context.getSharedPreferences("viewfinder", Context.MODE_PRIVATE)

    var gridOn: Boolean
        get() = prefs.getBoolean(KEY_GRID, false)
        set(value) = prefs.edit().putBoolean(KEY_GRID, value).apply()

    var levelOn: Boolean
        get() = prefs.getBoolean(KEY_LEVEL, false)
        set(value) = prefs.edit().putBoolean(KEY_LEVEL, value).apply()

    var timer: TimerChoice
        get() = enumOrDefault(prefs.getString(KEY_TIMER, null), TimerChoice.OFF)
        set(value) = prefs.edit().putString(KEY_TIMER, value.name).apply()

    var aspect: AspectChoice
        get() = enumOrDefault(prefs.getString(KEY_ASPECT, null), AspectChoice.FOUR_THREE)
        set(value) = prefs.edit().putString(KEY_ASPECT, value.name).apply()

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val KEY_GRID = "grid"
        const val KEY_LEVEL = "level"
        const val KEY_TIMER = "timer"
        const val KEY_ASPECT = "aspect"
    }
}
