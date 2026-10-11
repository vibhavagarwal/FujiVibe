package com.fujivibe.render

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Below this many items, splitting the work costs more than it saves. */
private const val MIN_ITEMS_PER_BAND = 32_768

/** Plenty to keep every core of a phone busy without drowning the scheduler in tiny tasks. */
private const val MAX_BANDS = 8

/**
 * Runs [work] over `0 until itemCount`, split into contiguous bands that run concurrently on the
 * caller's dispatcher. Each band is a disjoint range, so [work] may write straight into a shared
 * output array without locking. Used by the render loops, whose per-pixel work is independent.
 */
internal suspend fun parallelBands(itemCount: Int, work: (IntRange) -> Unit) {
    val bandCount = (itemCount / MIN_ITEMS_PER_BAND)
        .coerceIn(1, minOf(MAX_BANDS, Runtime.getRuntime().availableProcessors().coerceAtLeast(1)))
    if (bandCount == 1) {
        work(0 until itemCount)
        return
    }
    coroutineScope {
        val bandSize = (itemCount + bandCount - 1) / bandCount
        (0 until bandCount).map { band ->
            val start = band * bandSize
            val end = minOf(start + bandSize, itemCount)
            async { if (start < end) work(start until end) }
        }.awaitAll()
    }
}
