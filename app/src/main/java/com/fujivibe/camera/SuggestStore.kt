package com.fujivibe.camera

import android.content.Context

/** The Suggest sheet's ticks and which slots are locked, remembered across launches. */
class SuggestStore(context: Context) {
    private val prefs = context.getSharedPreferences("camera_slots", Context.MODE_PRIVATE)

    var subjects: Set<String>
        get() = TagSetCodec.decode(prefs.getString(KEY_SUBJECTS, ""))
        set(value) = prefs.edit().putString(KEY_SUBJECTS, TagSetCodec.encode(value)).apply()

    var light: Set<String>
        get() = TagSetCodec.decode(prefs.getString(KEY_LIGHT, ""))
        set(value) = prefs.edit().putString(KEY_LIGHT, TagSetCodec.encode(value)).apply()

    var mood: Set<String>
        get() = TagSetCodec.decode(prefs.getString(KEY_MOOD, ""))
        set(value) = prefs.edit().putString(KEY_MOOD, TagSetCodec.encode(value)).apply()

    /** On unless the user turns it off. */
    var includeMonochrome: Boolean
        get() = prefs.getBoolean(KEY_MONO, true)
        set(value) = prefs.edit().putBoolean(KEY_MONO, value).apply()

    /** On unless the user turns it off. */
    var hasFilter: Boolean
        get() = prefs.getBoolean(KEY_FILTER, true)
        set(value) = prefs.edit().putBoolean(KEY_FILTER, value).apply()

    var locked: Set<Int>
        get() = TagSetCodec.decode(prefs.getString(KEY_LOCKED, "")).mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 2..7 } }.toSet()
        set(value) = prefs.edit().putString(KEY_LOCKED, TagSetCodec.encode(value.map { it.toString() }.toSet())).apply()

    private companion object {
        const val KEY_SUBJECTS = "suggest_subjects"
        const val KEY_LIGHT = "suggest_light"
        const val KEY_MOOD = "suggest_mood"
        const val KEY_MONO = "suggest_mono"
        const val KEY_FILTER = "suggest_filter"
        const val KEY_LOCKED = "suggest_locked"
    }
}

/** A set of words as comma-separated text; blanks are dropped. */
object TagSetCodec {
    fun encode(tags: Set<String>): String = tags.sorted().joinToString(",")

    fun decode(text: String?): Set<String> = text.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
