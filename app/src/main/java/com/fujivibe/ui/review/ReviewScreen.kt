package com.fujivibe.ui.review

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.fujivibe.render.GrainRenderPipeline
import com.fujivibe.render.LutRenderPipeline
import com.fujivibe.render.RenderPipeline
import com.fujivibe.review.ReviewCycle
import com.fujivibe.review.SavedLooks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How far a horizontal drag must travel before it counts as a swipe rather than a tap wobble. */
private val SwipeThreshold = 56.dp

/** Persists the swipe position across Activity recreation (e.g. rotation). */
private val ReviewCycleSaver = Saver<ReviewCycle, Int>(
    save = { it.position },
    restore = { ReviewCycle.at(it) },
)

private val SavedLooksSaver = Saver<SavedLooks, ArrayList<String>>(
    save = { it.toKeys() },
    restore = { SavedLooks.fromKeys(it) },
)

/**
 * The entry actually on screen right now, paired with its label so the two can only ever change
 * together — [ReviewCycle] alone can race ahead of the async render it's driving.
 */
private data class DisplayedFrame(val cycle: ReviewCycle, val bitmap: Bitmap?)

/**
 * Shows the current Capture, swiping between Original and the launch Film Simulations (see
 * [ReviewCycle]). Export saves the current look and stays on Review so more looks can be saved
 * from the same Capture (ADR 0009); Discard/Done deletes the Capture and closes. Every entry — Original included — renders through the
 * same [RenderPipeline] call, so there's no UI-side rendering shortcut or duplicated LUT logic.
 */
@Composable
fun ReviewScreen(
    captureStore: CaptureStore,
    onClose: () -> Unit,
    galleryWriter: GalleryWriter,
    modifier: Modifier = Modifier,
    renderPipeline: RenderPipeline = remember { GrainRenderPipeline(LutRenderPipeline()) },
) {
    val previewSource = remember { captureStore.loadPreview()?.toPixelImage() }
    // Review can be restored from saved state after the cached Capture is gone (e.g. the OS
    // cleared cacheDir during process death); there's nothing to show, so leave.
    if (previewSource == null) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    var cycle by rememberSaveable(stateSaver = ReviewCycleSaver) { mutableStateOf(ReviewCycle.start()) }
    // Updated only once the matching render completes, so the label and the bitmap on screen
    // never point at two different entries mid-swipe.
    var displayed by remember { mutableStateOf(DisplayedFrame(cycle, bitmap = null)) }

    LaunchedEffect(cycle, previewSource) {
        displayed = DisplayedFrame(cycle, renderPipeline.render(previewSource, cycle.current).toBitmap())
    }

    // Full-resolution render + gallery write is slow (CPU LUT interpolation at real photo
    // resolution can take 10s of seconds - see CaptureStore.loadPreview's doc comment for the
    // preview-scale measurement this extrapolates from), so Export needs its own loading state
    // rather than looking hung on the UI thread's behalf.
    var isExporting by remember { mutableStateOf(false) }
    var savedLooks by rememberSaveable(stateSaver = SavedLooksSaver) { mutableStateOf(SavedLooks.NONE) }
    val snackbarHostState = remember { SnackbarHostState() }
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
            val shownSelection = displayed.cycle.current
            val isSaved = shownSelection in savedLooks

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            ) {
                Text(
                    text = if (isSaved) "${displayed.cycle.label}  ✓" else displayed.cycle.label,
                    color = Color.White,
                )
                PositionDots(
                    position = displayed.cycle.position,
                    count = displayed.cycle.count,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
            ) {
                Button(
                    onClick = {
                        captureStore.discard()
                        onClose()
                    },
                    enabled = !isExporting,
                ) {
                    Text(savedLooks.closeActionLabel)
                }

                Button(
                    enabled = !isExporting && displayed.bitmap != null && savedLooks.canExport(shownSelection),
                    onClick = {
                        isExporting = true
                        snackbarHostState.currentSnackbarData?.dismiss()
                        coroutineScope.launch {
                            // Decode is a blocking file read at full photo resolution; keep it off
                            // the coroutine's default dispatcher used by the render below.
                            val selection = shownSelection
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
                            // The Capture is kept either way: on success so more looks can be
                            // saved from it, on failure so Export can be retried.
                            if (saved) savedLooks += selection
                            snackbarHostState.showSnackbar(
                                if (saved) "Saved to Pictures/FujiVibe" else "Export failed - tap Export to retry"
                            )
                        }
                    },
                ) {
                    Text(if (isSaved) "Saved" else "Export")
                }
            }
        }
    }
}

/** A row of dots, one per [ReviewCycle] entry, with the current [position] filled in. */
@Composable
private fun PositionDots(position: Int, count: Int, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        repeat(count) { index ->
            val dot = Modifier.size(8.dp).clip(CircleShape)
            Box(
                if (index == position) {
                    dot.background(Color.White)
                } else {
                    dot.border(1.dp, Color.White, CircleShape)
                }
            )
        }
    }
}
