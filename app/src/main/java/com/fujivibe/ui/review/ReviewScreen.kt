package com.fujivibe.ui.review

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.fujivibe.capture.CaptureStore
import com.fujivibe.render.LutRenderPipeline
import com.fujivibe.render.RenderPipeline
import com.fujivibe.review.ReviewCycle

/** How far a horizontal drag must travel before it counts as a swipe rather than a tap wobble. */
private val SwipeThreshold = 56.dp

/**
 * Shows the current Capture, swiping between Original and the launch Film Simulations (see
 * [ReviewCycle]) with a Discard control. Every entry — Original included — renders through the
 * same [RenderPipeline] call, so there's no UI-side rendering shortcut or duplicated LUT logic.
 */
@Composable
fun ReviewScreen(
    captureStore: CaptureStore,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    renderPipeline: RenderPipeline = remember { LutRenderPipeline() },
) {
    val previewSource = remember { captureStore.loadPreview()?.toPixelImage() }
    var cycle by remember { mutableStateOf(ReviewCycle.start()) }
    var renderedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(cycle, previewSource) {
        val source = previewSource ?: return@LaunchedEffect
        renderedBitmap = renderPipeline.render(source, cycle.current).toBitmap()
    }

    val swipeThresholdPx = with(LocalDensity.current) { SwipeThreshold.toPx() }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var dragAmount = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragAmount = 0f },
                    onHorizontalDrag = { change, delta ->
                        change.consume()
                        dragAmount += delta
                    },
                    onDragEnd = {
                        when {
                            dragAmount <= -swipeThresholdPx -> cycle = cycle.next()
                            dragAmount >= swipeThresholdPx -> cycle = cycle.previous()
                        }
                    },
                )
            },
    ) {
        renderedBitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = cycle.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Text(
                text = cycle.label,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            )

            Button(
                onClick = {
                    captureStore.discard()
                    onDiscard()
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
            ) {
                Text("Discard")
            }
        }
    }
}
