package com.fujivibe.ui.viewfinder

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fujivibe.R
import com.fujivibe.ui.theme.Amber
import com.fujivibe.ui.theme.Ink
import com.fujivibe.ui.theme.Paper
import com.fujivibe.viewfinder.AspectChoice
import com.fujivibe.viewfinder.TimerChoice
import com.fujivibe.viewfinder.isLevel
import com.fujivibe.viewfinder.photoFrameInView

/**
 * A quiet "FUJIVIBE" wordmark on the left, the way camera makers print their name on the body;
 * grid, level, timer and aspect-ratio toggles on the right, amber while on.
 */
@Composable
fun TopBar(
    gridOn: Boolean,
    onGridToggle: () -> Unit,
    levelOn: Boolean,
    onLevelToggle: () -> Unit,
    timer: TimerChoice,
    onTimerNext: () -> Unit,
    aspect: AspectChoice,
    onAspectNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            "FUJIVIBE",
            color = Paper.copy(alpha = 0.55f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 3.sp,
        )
        Box(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            ToggleIcon(R.drawable.ic_grid_on, "Grid", active = gridOn, onClick = onGridToggle)
            ToggleIcon(R.drawable.ic_straighten, "Level line", active = levelOn, onClick = onLevelToggle)
            ToggleIcon(
                when (timer) {
                    TimerChoice.OFF -> R.drawable.ic_timer
                    TimerChoice.THREE -> R.drawable.ic_timer_3_alt_1
                    TimerChoice.TEN -> R.drawable.ic_timer_10_alt_1
                },
                "Self-timer",
                active = timer != TimerChoice.OFF,
                onClick = onTimerNext,
            )
            Text(
                aspect.label,
                color = Paper,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onAspectNext)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ToggleIcon(@DrawableRes icon: Int, description: String, active: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onClick),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = if (active) Amber else Paper,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Drawn over the preview: bars outside the photo's frame (so only what will be saved shows),
 * rule-of-thirds [grid] lines and the level line inside it. [horizonAngle] is null when the
 * phone lies flat or the level line is off.
 */
@Composable
fun FrameOverlay(aspect: AspectChoice, grid: Boolean, horizonAngle: Float?, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val frame = photoFrameInView(size.width, size.height, aspect)
        if (frame.top > 0f) {
            drawRect(Ink, Offset.Zero, Size(size.width, frame.top))
            drawRect(Ink, Offset(0f, frame.top + frame.height), Size(size.width, size.height - frame.top - frame.height))
        }
        if (frame.left > 0f) {
            drawRect(Ink, Offset.Zero, Size(frame.left, size.height))
            drawRect(Ink, Offset(frame.left + frame.width, 0f), Size(size.width - frame.left - frame.width, size.height))
        }

        if (grid) {
            val line = Paper.copy(alpha = 0.35f)
            val stroke = 1.dp.toPx()
            for (i in 1..2) {
                val x = frame.left + frame.width * i / 3
                val y = frame.top + frame.height * i / 3
                drawLine(line, Offset(x, frame.top), Offset(x, frame.top + frame.height), stroke)
                drawLine(line, Offset(frame.left, y), Offset(frame.left + frame.width, y), stroke)
            }
        }

        if (horizonAngle != null) {
            val level = isLevel(horizonAngle)
            val center = Offset(frame.left + frame.width / 2, frame.top + frame.height / 2)
            val half = minOf(frame.width, frame.height) * 0.22f
            val stroke = (if (level) 2.5.dp else 1.5.dp).toPx()
            // Short fixed marks either side show where level is; the middle line follows the horizon.
            val mark = Paper.copy(alpha = 0.6f)
            drawLine(mark, Offset(center.x - half * 1.5f, center.y), Offset(center.x - half * 1.15f, center.y), 1.5.dp.toPx())
            drawLine(mark, Offset(center.x + half * 1.15f, center.y), Offset(center.x + half * 1.5f, center.y), 1.5.dp.toPx())
            rotate(horizonAngle, pivot = center) {
                drawLine(
                    if (level) Amber else Paper,
                    Offset(center.x - half, center.y),
                    Offset(center.x + half, center.y),
                    stroke,
                    StrokeCap.Round,
                )
            }
        }
    }
}

/** Big self-timer countdown in the middle of the frame. */
@Composable
fun Countdown(secondsLeft: Int, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$secondsLeft", color = Paper, fontSize = 120.sp)
    }
}

/** Brief dark flash over the preview when the shutter fires; [alpha] animates 0 to 1 to 0. */
@Composable
fun ShutterBlink(alpha: Float, modifier: Modifier = Modifier) {
    if (alpha > 0f) {
        Canvas(modifier.fillMaxSize()) { drawRect(Color.Black.copy(alpha = alpha)) }
    }
}
