package com.fujivibe.ui.review

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.fujivibe.capture.shootingInfoLine
import com.fujivibe.ui.theme.Amber
import com.fujivibe.ui.theme.Paper
import com.fujivibe.ui.theme.PaperMuted
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
    // What the shot was taken with, as a camera's playback display shows it.
    val shootingInfo = remember { captureStore.shootingInfo()?.let(::shootingInfoLine) }

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

            fun close() {
                captureStore.discard()
                onClose()
            }

            fun export() {
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
                        galleryWriter.save(
                            renderPipeline.render(fullResSource, selection),
                            metadataFrom = captureStore.metadataSource(),
                        )

                    isExporting = false
                    // The Capture is kept either way: on success so more looks can be
                    // saved from it, on failure so Export can be retried.
                    if (saved) savedLooks += selection
                    snackbarHostState.showSnackbar(
                        if (saved) "Saved to Pictures/FujiVibe" else "Export failed. Tap Export to retry."
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            ) {
                LookTitle(label = displayed.cycle.label, saved = isSaved)
                PositionDots(
                    position = displayed.cycle.position,
                    count = displayed.cycle.count,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 132.dp),
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                shootingInfo?.let { Text(it, color = PaperMuted, fontSize = 14.sp) }

                // A saved look has nothing left to export (its title already carries the
                // checkmark), so Done stands alone, centered, as the one strong button.
                if (isSaved) {
                    PrimaryButton(text = savedLooks.closeActionLabel, enabled = !isExporting, onClick = ::close)
                } else {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(onClick = ::close, enabled = !isExporting) {
                            Text(savedLooks.closeActionLabel, color = Paper, fontSize = 16.sp)
                        }
                        PrimaryButton(
                            text = "Export",
                            enabled = !isExporting && displayed.bitmap != null && savedLooks.canExport(shownSelection),
                            onClick = ::export,
                        )
                    }
                }
            }
        }
    }
}

/** The look's name, its "(Pixel)" suffix muted, and an amber check once it's been saved. */
@Composable
private fun LookTitle(label: String, saved: Boolean) {
    val match = Regex("""^(.*?)(\s*\(.*\))$""").find(label)
    val name = match?.groupValues?.get(1) ?: label
    val suffix = match?.groupValues?.get(2).orEmpty()
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Paper, fontWeight = FontWeight.Medium)) { append(name) }
            withStyle(SpanStyle(color = PaperMuted)) { append(suffix) }
            if (saved) withStyle(SpanStyle(color = Amber)) { append("  ✓") }
        },
        fontSize = 20.sp,
    )
}

/** The screen's one strong action, filled in amber. */
@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 10.dp),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** A row of dots, one per [ReviewCycle] entry, with the current [position] in amber. */
@Composable
private fun PositionDots(position: Int, count: Int, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = modifier) {
        repeat(count) { index ->
            val dot = Modifier.size(7.dp).clip(CircleShape)
            Box(
                if (index == position) {
                    dot.background(Amber)
                } else {
                    dot.border(1.dp, PaperMuted, CircleShape)
                }
            )
        }
    }
}
