package com.fujivibe.camera

import android.content.Context

/** Which recipe the user chose for each slot (C2 to C7), remembered across launches. */
class SlotPlanStore(context: Context) {
    private val prefs = context.getSharedPreferences("camera_slots", Context.MODE_PRIVATE)

    var plan: Map<Int, String>
        get() = SlotPlanCodec.decode(prefs.getString(KEY_PLAN, "").orEmpty())
        set(value) = prefs.edit().putString(KEY_PLAN, SlotPlanCodec.encode(value)).apply()

    private companion object {
        const val KEY_PLAN = "plan"
    }
}

/** The plan as text, `2=reggies-portra;3=pacific-blues`, so a bad entry can be dropped without losing the rest. */
object SlotPlanCodec {
    fun encode(plan: Map<Int, String>): String =
        plan.toSortedMap().entries.joinToString(";") { "${it.key}=${it.value}" }

    fun decode(text: String): Map<Int, String> = text.split(";").mapNotNull {
        val parts = it.split("=", limit = 2)
        if (parts.size != 2) return@mapNotNull null
        val n = parts[0].toIntOrNull()?.takeIf { s -> s in 2..7 } ?: return@mapNotNull null
        if (parts[1].isBlank()) null else n to parts[1]
    }.toMap()
}
