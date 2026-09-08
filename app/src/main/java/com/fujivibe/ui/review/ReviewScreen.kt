package com.fujivibe.ui.review

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.fujivibe.bitmap.toBitmap
import com.fujivibe.bitmap.toPixelImage
import com.fujivibe.capture.CaptureStore
import com.fujivibe.gallery.GalleryWriter
import com.fujivibe.render.LutRenderPipeline
import com.fujivibe.render.RenderPipeline
import com.fujivibe.review.ReviewCycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How far a horizontal drag must travel before it counts as a swipe rather than a tap wobble. */
private val SwipeThreshold = 56.dp

/**
 * The entry actually on screen right now, paired with its label so the two can only ever change
 * together — [ReviewCycle] alone can race ahead of the async render it's driving.
 */
private data class DisplayedFrame(val cycle: ReviewCycle, val bitmap: Bitmap?)

/**
 * Shows the current Capture, swiping between Original and the launch Film Simulations (see
 * [ReviewCycle]) with a Discard control. Every entry — Original included — renders through the
 * same [RenderPipeline] call, so there's no UI-side rendering shortcut or duplicated LUT logic.
 */
@Composable
fun ReviewScreen(
    captureStore: CaptureStore,
    onDiscard: () -> Unit,
    onExported: () -> Unit,
    galleryWriter: GalleryWriter,
    modifier: Modifier = Modifier,
    renderPipeline: RenderPipeline = remember { LutRenderPipeline() },
) {
    val previewSource = remember { captureStore.loadPreview()?.toPixelImage() }
    var cycle by remember { mutableStateOf(ReviewCycle.start()) }
    // Updated only once the matching render completes, so the label and the bitmap on screen
    // never point at two different entries mid-swipe.
    var displayed by remember { mutableStateOf(DisplayedFrame(cycle, bitmap = null)) }

    LaunchedEffect(cycle, previewSource) {
        val source = previewSource ?: return@LaunchedEffect
        displayed = DisplayedFrame(cycle, renderPipeline.render(source, cycle.current).toBitmap())
    }

    // Full-resolution render + gallery write is slow (CPU LUT interpolation at real photo
    // resolution can take 10s of seconds - see CaptureStore.loadPreview's doc comment for the
    // preview-scale measurement this extrapolates from), so Export needs its own loading state
    // rather than looking hung on the UI thread's behalf.
    var isExporting by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
        displayed.bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = displayed.cycle.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Text(
                text = displayed.cycle.label,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            )

            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            if (exportFailed) {
                Text(
                    text = "Export failed — you can try again",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
            ) {
                Button(
                    onClick = {
                        captureStore.discard()
                        onDiscard()
                    },
                    enabled = !isExporting,
                ) {
                    Text("Discard")
                }

                Button(
                    enabled = !isExporting,
                    onClick = {
                        exportFailed = false
                        isExporting = true
                        coroutineScope.launch {
                            // Decode is a blocking file read at full photo resolution; keep it off
                            // the coroutine's default dispatcher used by the render below.
                            val selection = displayed.cycle.current
                            // A full-res capture's Bitmap and its PixelImage copy are both
                            // ~tens of MB; recycle the Bitmap the moment its pixels are copied
                            // out instead of waiting on the GC to reclaim it mid-export.
                            val fullResSource = withContext(Dispatchers.IO) {
                                val bitmap = captureStore.loadFullResolution() ?: return@withContext null
                                bitmap.toPixelImage().also { bitmap.recycle() }
                            }
                            val saved = fullResSource != null &&
                                galleryWriter.save(renderPipeline.render(fullResSource, selection))

                            isExporting = false
                            if (saved) {
                                captureStore.discard()
                                onExported()
                            } else {
                                exportFailed = true
                            }
                        }
                    },
                ) {
                    Text("Export")
                }
            }
        }
    }
}
