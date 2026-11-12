package com.fujivibe.camera

import android.content.Context

/** One successful load: when it happened and which recipe (by id) went into each slot. */
data class LoadEntry(val at: Long, val plan: Map<Int, String>)

/** The loads so far as text, `1700000000000:2=a;3=b|1690000000000:2=c`, so a bad entry can be dropped alone. */
object LoadHistoryCodec {
    fun encode(entries: List<LoadEntry>): String =
        entries.joinToString("|") { "${it.at}:${SlotPlanCodec.encode(it.plan)}" }

    fun decode(text: String): List<LoadEntry> = text.split("|").mapNotNull {
        val parts = it.split(":", limit = 2)
        if (parts.size != 2) return@mapNotNull null
        val at = parts[0].toLongOrNull() ?: return@mapNotNull null
        val plan = SlotPlanCodec.decode(parts[1])
        if (plan.isEmpty()) null else LoadEntry(at, plan)
    }
}

/**
 * [entries] with [entry] first (newest first). A load of the same recipes as the latest one replaces it
 * rather than repeating it, and only the newest [max] are kept.
 */
fun List<LoadEntry>.withRecorded(entry: LoadEntry, max: Int = LoadHistoryStore.MAX_ENTRIES): List<LoadEntry> =
    (listOf(entry) + if (firstOrNull()?.plan == entry.plan) drop(1) else this).take(max)

/** The loads that worked, newest first, so an earlier set can be brought back as the plan. */
class LoadHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("camera_slots", Context.MODE_PRIVATE)

    var entries: List<LoadEntry>
        get() = LoadHistoryCodec.decode(prefs.getString(KEY, "").orEmpty())
        private set(value) = prefs.edit().putString(KEY, LoadHistoryCodec.encode(value)).apply()

    /** Records a successful load; returns the history after it. */
    fun record(plan: Map<Int, String>, at: Long = System.currentTimeMillis()): List<LoadEntry> {
        if (plan.isEmpty()) return entries
        entries = entries.withRecorded(LoadEntry(at, plan))
        return entries
    }

    companion object {
        const val MAX_ENTRIES = 10
        private const val KEY = "history"
    }
}
