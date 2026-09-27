package com.fujivibe.ui.viewfinder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fujivibe.R
import com.fujivibe.viewfinder.AspectChoice
import com.fujivibe.viewfinder.TimerChoice
import com.fujivibe.viewfinder.isLevel
import com.fujivibe.viewfinder.photoFrameInView

/** Gold used by the level line once the phone is level. */
private val LevelGold = Color(0xFFFFD479)

/** Logo on the left; grid, level, timer and aspect-ratio toggles on the right. */
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_monochrome),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(34.dp),
        )
        Text("FujiVibe", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ToggleButton(active = gridOn, onClick = onGridToggle) { GridIcon(it) }
            ToggleButton(active = levelOn, onClick = onLevelToggle) { LevelIcon(it) }
            ToggleButton(active = timer != TimerChoice.OFF, onClick = onTimerNext) { color ->
                if (timer == TimerChoice.OFF) TimerIcon(color) else Text("${timer.seconds}s", color = color, fontSize = 13.sp)
            }
            Text(
                aspect.label,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .background(PanelBackground, RoundedCornerShape(14.dp))
                    .clickable(onClick = onAspectNext)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** A round toggle; its icon turns the panel accent color while on. */
@Composable
private fun ToggleButton(active: Boolean, onClick: () -> Unit, icon: @Composable (Color) -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(PanelBackground)
            .clickable(onClick = onClick),
    ) {
        icon(if (active) PanelAccent else Color.White)
    }
}

@Composable
private fun GridIcon(color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val stroke = 1.5.dp.toPx()
        drawRect(color, style = Stroke(stroke))
        for (i in 1..2) {
            val x = size.width * i / 3
            val y = size.height * i / 3
            drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
            drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}

@Composable
private fun LevelIcon(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val stroke = 1.8.dp.toPx()
        val midY = size.height / 2
        drawLine(color, Offset(0f, midY), Offset(size.width * 0.3f, midY), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.7f, midY), Offset(size.width, midY), stroke, StrokeCap.Round)
        drawCircle(color, radius = size.width * 0.14f, center = Offset(size.width / 2, midY), style = Stroke(stroke))
    }
}

@Composable
private fun TimerIcon(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val stroke = 1.8.dp.toPx()
        val c = Offset(size.width / 2, size.height * 0.56f)
        val r = size.width * 0.38f
        drawCircle(color, radius = r, center = c, style = Stroke(stroke))
        drawLine(color, c, Offset(c.x, c.y - r * 0.6f), stroke, StrokeCap.Round)
        drawLine(color, c, Offset(c.x + r * 0.45f, c.y), stroke, StrokeCap.Round)
        drawLine(color, Offset(c.x - r * 0.35f, 1.dp.toPx()), Offset(c.x + r * 0.35f, 1.dp.toPx()), stroke, StrokeCap.Round)
    }
}

/**
 * Drawn over the preview: black bars outside the photo's frame (so only what will be saved
 * shows), rule-of-thirds [grid] lines and the level line inside it. [horizonAngle] is null when
 * the phone lies flat or the level line is off.
 */
@Composable
fun FrameOverlay(aspect: AspectChoice, grid: Boolean, horizonAngle: Float?, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val frame = photoFrameInView(size.width, size.height, aspect)
        val bar = Color.Black
        if (frame.top > 0f) {
            drawRect(bar, Offset.Zero, Size(size.width, frame.top))
            drawRect(bar, Offset(0f, frame.top + frame.height), Size(size.width, size.height - frame.top - frame.height))
        }
        if (frame.left > 0f) {
            drawRect(bar, Offset.Zero, Size(frame.left, size.height))
            drawRect(bar, Offset(frame.left + frame.width, 0f), Size(size.width - frame.left - frame.width, size.height))
        }

        if (grid) {
            val line = Color.White.copy(alpha = 0.4f)
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
            val mark = Color.White.copy(alpha = 0.6f)
            drawLine(mark, Offset(center.x - half * 1.5f, center.y), Offset(center.x - half * 1.15f, center.y), 1.5.dp.toPx())
            drawLine(mark, Offset(center.x + half * 1.15f, center.y), Offset(center.x + half * 1.5f, center.y), 1.5.dp.toPx())
            rotate(horizonAngle, pivot = center) {
                drawLine(
                    if (level) LevelGold else Color.White,
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
        Text("$secondsLeft", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Light)
    }
}
