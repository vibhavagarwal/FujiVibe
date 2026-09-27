package com.fujivibe.viewfinder

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The photo's shape, as long side : short side. 4:3 is the sensor's own shape; 3:2 is the shape
 * of Fujifilm camera photos. The Capture itself is cropped, so Review and Export follow it.
 */
enum class AspectChoice(val label: String, val long: Int, val short: Int) {
    FOUR_THREE("4:3", 4, 3),
    THREE_TWO("3:2", 3, 2),
    ONE_ONE("1:1", 1, 1),
    SIXTEEN_NINE("16:9", 16, 9);

    fun next(): AspectChoice = entries[(ordinal + 1) % entries.size]
}

/** Self-timer delay before the shutter fires. */
enum class TimerChoice(val seconds: Int) {
    OFF(0),
    THREE(3),
    TEN(10);

    fun next(): TimerChoice = entries[(ordinal + 1) % entries.size]
}

/** A rectangle in view pixels. */
data class FrameRect(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Where a photo of [aspect] appears inside a [viewWidth] x [viewHeight] preview when fitted and
 * centered: the long side runs along the view's longer dimension, as the camera frames it.
 */
fun photoFrameInView(viewWidth: Float, viewHeight: Float, aspect: AspectChoice): FrameRect {
    val portrait = viewHeight >= viewWidth
    // width / height of the photo as it appears on screen.
    val shape = if (portrait) aspect.short.toFloat() / aspect.long else aspect.long.toFloat() / aspect.short
    val width: Float
    val height: Float
    if (viewWidth / viewHeight > shape) {
        height = viewHeight
        width = height * shape
    } else {
        width = viewWidth
        height = width / shape
    }
    return FrameRect((viewWidth - width) / 2, (viewHeight - height) / 2, width, height)
}

/**
 * The horizon's angle on screen, in degrees clockwise, from the gravity vector ([x], [y], [z])
 * in the phone's own axes and how far the screen is turned from the phone's natural upright
 * ([displayRotationDegrees], 0/90/180/270). 0 means the horizon runs straight across the screen.
 * null when the phone lies nearly flat, where a horizon is meaningless.
 */
fun horizonAngle(x: Float, y: Float, z: Float, displayRotationDegrees: Int): Float? {
    val total = sqrt(x * x + y * y + z * z)
    if (total == 0f || hypot(x, y) < 0.35f * total) return null
    val deviceAngle = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat()
    return normalizeDegrees(deviceAngle - displayRotationDegrees)
}

/** True when [angle] is within [toleranceDegrees] of level, in any of the four orientations. */
fun isLevel(angle: Float, toleranceDegrees: Float = 1f): Boolean {
    val offFromRightAngle = abs(((angle % 90f) + 90f) % 90f)
    return offFromRightAngle <= toleranceDegrees || offFromRightAngle >= 90f - toleranceDegrees
}

/** [degrees] folded into -180..180. */
private fun normalizeDegrees(degrees: Float): Float {
    var d = degrees % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}
