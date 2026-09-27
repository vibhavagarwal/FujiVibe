package com.fujivibe.ui.viewfinder

import androidx.compose.foundation.border
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.fujivibe.R
import com.fujivibe.ui.theme.Amber
import com.fujivibe.ui.theme.Ink
import com.fujivibe.ui.theme.OnAmber
import com.fujivibe.ui.theme.Paper
import com.fujivibe.ui.theme.PaperMuted
import com.fujivibe.ui.theme.Scrim
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Highlight for the selected value: the app's one accent. */
val PanelAccent = Amber

/** Translucent backing for controls drawn over the live preview. */
val PanelBackground = Scrim

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
            .background(if (selected) Paper else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, color = if (selected) Ink else Paper, fontSize = 15.sp)
        if (cameraChosen) {
            Text(
                "A",
                color = if (selected) Ink else Paper,
                fontSize = 10.sp,
                modifier = Modifier
                    .border(1.dp, if (selected) Ink else PaperMuted, RoundedCornerShape(3.dp))
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
                color = Paper.copy(alpha = if (major) 0.9f else 0.45f),
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
        color = if (active) OnAmber else Paper,
        fontSize = 14.sp,
        modifier = Modifier
            .background(if (active) PanelAccent else Paper.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Switch-camera icon (Material Symbols "cameraswitch"). */
@Composable
fun SwitchCameraIcon(modifier: Modifier = Modifier) {
    Icon(
        painter = painterResource(R.drawable.ic_cameraswitch),
        contentDescription = "Switch camera",
        tint = Paper,
        modifier = modifier.size(26.dp),
    )
}
