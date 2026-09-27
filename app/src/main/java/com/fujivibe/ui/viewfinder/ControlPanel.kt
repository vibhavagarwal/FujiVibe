package com.fujivibe.ui.viewfinder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Highlight for the selected value, as in camera-app control panels. */
val PanelAccent = Color(0xFF6FD3FF)

/** Translucent backing for controls drawn over the live preview. */
val PanelBackground = Color.Black.copy(alpha = 0.55f)

private val RulerSidePadding = 12.dp

/**
 * One tab in the control row: the setting's current value. [cameraChosen] adds a small "A" badge
 * when the camera, not the user, picked that value to keep the brightness right.
 */
@Composable
fun ControlTab(text: String, selected: Boolean, cameraChosen: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = if (selected) Color.Black else Color.White, fontSize = 14.sp)
        if (cameraChosen) {
            Text(
                "A",
                color = if (selected) Color.White else Color.Black,
                fontSize = 10.sp,
                modifier = Modifier
                    .background(if (selected) Color.Black else Color.White, RoundedCornerShape(4.dp))
                    .padding(horizontal = 3.dp),
            )
        }
    }
}

/**
 * A tick-mark ruler: drag or tap anywhere on it to move the marker. [fraction] is the marker's
 * position, `0..1`; [tickCount] ticks are spread evenly, every [majorEvery]th drawn taller.
 * [dimmed] draws the marker faded, for a value the camera chose rather than the user.
 */
@Composable
fun Ruler(
    fraction: Float,
    onFraction: (Float) -> Unit,
    tickCount: Int,
    majorEvery: Int,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val latestOnFraction by rememberUpdatedState(onFraction)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                val side = RulerSidePadding.toPx()
                fun report(x: Float) {
                    val usable = (size.width - 2 * side).coerceAtLeast(1f)
                    latestOnFraction(((x - side) / usable).coerceIn(0f, 1f))
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    report(down.position.x)
                    drag(down.id) { change ->
                        report(change.position.x)
                        change.consume()
                    }
                }
            },
    ) {
        val side = RulerSidePadding.toPx()
        val usable = size.width - 2 * side
        val bottom = size.height - 4.dp.toPx()
        val count = tickCount.coerceAtLeast(2)
        for (i in 0 until count) {
            val x = side + usable * i / (count - 1)
            val major = i % majorEvery == 0
            drawLine(
                color = Color.White.copy(alpha = if (major) 0.9f else 0.5f),
                start = Offset(x, bottom - (if (major) 14.dp else 7.dp).toPx()),
                end = Offset(x, bottom),
                strokeWidth = 1.dp.toPx(),
            )
        }
        val markerX = side + usable * fraction.coerceIn(0f, 1f)
        drawLine(
            color = PanelAccent.copy(alpha = if (dimmed) 0.45f else 1f),
            start = Offset(markerX, 2.dp.toPx()),
            end = Offset(markerX, bottom),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** A small reset button beside the ruler ("Auto", or "1x" for zoom); filled while active. */
@Composable
fun ResetButton(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) Color.Black else Color.White,
        fontSize = 13.sp,
        modifier = Modifier
            .background(if (active) PanelAccent else Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Switch-camera icon: a small camera inside two circling arrows. */
@Composable
fun SwitchCameraIcon(modifier: Modifier = Modifier) {
    Canvas(modifier.size(26.dp)) {
        val stroke = 1.8.dp.toPx()
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 - stroke

        // Camera body and lens.
        val bodyW = size.width * 0.44f
        val bodyH = size.height * 0.30f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(c.x - bodyW / 2, c.y - bodyH / 2),
            size = Size(bodyW, bodyH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            style = Stroke(stroke),
        )
        drawCircle(Color.White, radius = bodyH * 0.28f, center = c, style = Stroke(stroke))

        // Two arcs, each ending in an arrowhead, circling the camera.
        for (startDeg in listOf(200f, 20f)) {
            val sweep = 120f
            drawArc(
                color = Color.White,
                startAngle = startDeg,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(2 * r, 2 * r),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val endRad = Math.toRadians((startDeg + sweep).toDouble())
            val tip = Offset(c.x + r * cos(endRad).toFloat(), c.y + r * sin(endRad).toFloat())
            // Arrowhead points along the direction of travel (clockwise tangent).
            val dir = Offset(-sin(endRad).toFloat(), cos(endRad).toFloat())
            val normal = Offset(cos(endRad).toFloat(), sin(endRad).toFloat())
            val head = 4.dp.toPx()
            val path = Path().apply {
                moveTo(tip.x + dir.x * head * 0.6f, tip.y + dir.y * head * 0.6f)
                lineTo(tip.x - dir.x * head * 0.6f + normal.x * head, tip.y - dir.y * head * 0.6f + normal.y * head)
                lineTo(tip.x - dir.x * head * 0.6f - normal.x * head, tip.y - dir.y * head * 0.6f - normal.y * head)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}
