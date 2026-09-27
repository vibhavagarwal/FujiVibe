package com.fujivibe.viewfinder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ViewfinderOptionsTest {

    @Test
    fun `aspect and timer choices cycle and wrap around`() {
        assertEquals(AspectChoice.THREE_TWO, AspectChoice.FOUR_THREE.next())
        assertEquals(AspectChoice.FOUR_THREE, AspectChoice.SIXTEEN_NINE.next())
        assertEquals(TimerChoice.THREE, TimerChoice.OFF.next())
        assertEquals(TimerChoice.OFF, TimerChoice.TEN.next())
    }

    @Test
    fun `a 4 by 3 photo in a tall view is full width and three quarters as wide as tall`() {
        val frame = photoFrameInView(1080f, 2400f, AspectChoice.FOUR_THREE)
        assertEquals(1080f, frame.width, 0.01f)
        assertEquals(1440f, frame.height, 0.01f)
        assertEquals(480f, frame.top, 0.01f)
        assertEquals(0f, frame.left, 0.01f)
    }

    @Test
    fun `a square photo in a tall view is full width and centered`() {
        val frame = photoFrameInView(1080f, 2400f, AspectChoice.ONE_ONE)
        assertEquals(1080f, frame.width, 0.01f)
        assertEquals(1080f, frame.height, 0.01f)
        assertEquals(660f, frame.top, 0.01f)
    }

    @Test
    fun `in a wide view the long side runs across`() {
        val frame = photoFrameInView(2400f, 1080f, AspectChoice.THREE_TWO)
        assertEquals(1080f, frame.height, 0.01f)
        assertEquals(1620f, frame.width, 0.01f)
        assertEquals(390f, frame.left, 0.01f)
    }

    @Test
    fun `an upright phone reads a level horizon`() {
        assertEquals(0f, horizonAngle(0f, 9.8f, 0f, 0)!!, 0.01f)
    }

    @Test
    fun `tilting the phone clockwise turns the horizon the other way on screen`() {
        // Clockwise by 10 degrees: gravity's "up" leans toward the phone's upper left.
        val angle = horizonAngle(-1.70f, 9.65f, 0f, 0)!!
        assertEquals(-10f, angle, 0.2f)
        assertFalse(isLevel(angle))
    }

    @Test
    fun `a sideways phone with a sideways screen is level`() {
        // Turned 90 degrees counter-clockwise, and the screen turned with it.
        val angle = horizonAngle(9.8f, 0f, 0f, 90)
        assertNotNull(angle)
        assertEquals(0f, angle!!, 0.01f)
    }

    @Test
    fun `a phone lying flat has no horizon`() {
        assertNull(horizonAngle(0.5f, 0.5f, 9.8f, 0))
    }

    @Test
    fun `level holds near any right angle`() {
        assertTrue(isLevel(0.5f))
        assertTrue(isLevel(-89.4f))
        assertTrue(isLevel(179.6f))
        assertFalse(isLevel(3f))
    }
}
