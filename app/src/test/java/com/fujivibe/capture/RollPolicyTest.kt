package com.fujivibe.capture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class RollPolicyTest {

    private val now = 1_800_000_000_000L
    private fun daysAgo(days: Long) = now - TimeUnit.DAYS.toMillis(days)

    @Test
    fun `the Roll is full at 50 Captures, not before`() {
        assertFalse(RollPolicy.isFull(49))
        assertTrue(RollPolicy.isFull(50))
    }

    @Test
    fun `a Capture expires after 30 days`() {
        assertFalse(RollPolicy.isExpired(daysAgo(29), now))
        assertTrue(RollPolicy.isExpired(daysAgo(30), now))
    }

    @Test
    fun `expired picks out only the old Captures`() {
        val ids = listOf(daysAgo(1), daysAgo(40), daysAgo(31), daysAgo(10))

        assertEquals(listOf(daysAgo(40), daysAgo(31)), RollPolicy.expired(ids, now))
    }

    @Test
    fun `days left counts down and stops at zero`() {
        assertEquals(30, RollPolicy.daysLeft(now, now))
        assertEquals(20, RollPolicy.daysLeft(daysAgo(10), now))
        assertEquals(0, RollPolicy.daysLeft(daysAgo(45), now))
    }
}
