package com.fujivibe.capture

import java.util.concurrent.TimeUnit

/**
 * The rules for the Roll, the Captures waiting to be developed. A Capture's id is the time it was
 * taken, in epoch milliseconds, so its age needs no separate bookkeeping.
 */
object RollPolicy {
    const val MAX_CAPTURES = 50
    val EXPIRY_MILLIS: Long = TimeUnit.DAYS.toMillis(30)

    /** A full Roll takes no more shots until one is developed or discarded. */
    fun isFull(count: Int): Boolean = count >= MAX_CAPTURES

    fun isExpired(captureId: Long, nowMillis: Long): Boolean = nowMillis - captureId >= EXPIRY_MILLIS

    fun expired(captureIds: List<Long>, nowMillis: Long): List<Long> =
        captureIds.filter { isExpired(it, nowMillis) }

    /** Whole days left before [captureId] expires, never below 0. */
    fun daysLeft(captureId: Long, nowMillis: Long): Int =
        TimeUnit.MILLISECONDS.toDays((EXPIRY_MILLIS - (nowMillis - captureId)).coerceAtLeast(0)).toInt()
}
