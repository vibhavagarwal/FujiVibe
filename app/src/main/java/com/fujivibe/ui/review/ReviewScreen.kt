package com.fujivibe.ui.review

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.fujivibe.capture.shootingInfoLine
import com.fujivibe.ui.theme.Amber
import com.fujivibe.ui.theme.Paper
import com.fujivibe.ui.theme.PaperMuted
import com.fujivibe.ui.theme.Scrim
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.fujivibe.R
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import android.util.LruCache
import com.fujivibe.bitmap.toBitmap
import com.fujivibe.bitmap.toPixelImage
import com.fujivibe.capture.StoredCapture
import com.fujivibe.gallery.GalleryWriter
import com.fujivibe.render.GrainRenderPipeline
import com.fujivibe.render.LutRenderPipeline
import com.fujivibe.render.PixelImage
import com.fujivibe.render.RenderPipeline
import com.fujivibe.review.ReviewCycle
import com.fujivibe.review.SavedLooks
import com.fujivibe.ui.zoom.rememberZoomState
import com.fujivibe.ui.zoom.zoomLayer
import com.fujivibe.ui.zoom.zoomable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How far a horizontal drag must travel before it counts as a swipe rather than a tap wobble. */
private val SwipeThreshold = 56.dp

/** Widest the Discard/Export row gets; about a phone held upright. */
private val BottomBarMaxWidth = 400.dp

/**
 * Longer-edge cap for the sharper render shown while zoomed in. The ~510px preview is far too
 * soft to magnify, but full resolution would take tens of seconds to render, so this lands
 * `loadPreview` on its ~2000px bracket (about 3MP, a couple of seconds on-device).
 */
private const val DetailMaxDimension = 1500

/** How many looks' detail renders to keep, so zooming back into a look you've seen is instant. */
private const val DetailCacheSize = 3

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
    captureStore: StoredCapture,
    onClose: () -> Unit,
    onBack: () -> Unit,
    galleryWriter: GalleryWriter,
    initialSavedLooks: SavedLooks,
    onSavedLooksChanged: (SavedLooks) -> Unit,
    modifier: Modifier = Modifier,
    renderPipeline: RenderPipeline = remember { GrainRenderPipeline(LutRenderPipeline()) },
) {
    // Leaving without deciding keeps the Capture on the Roll for later.
    BackHandler(onBack = onBack)
    val previewSource = remember { captureStore.loadPreview()?.toPixelImage() }
    // Review can be restored from saved state after its Capture is gone (e.g. it expired while
    // the app was closed); there's nothing to show, so leave.
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

    // Pinch-zoom and pan, shared by every look. Zooming in swaps the soft preview for a sharper
    // render of the same look, produced on demand (the first zoom pays for the decode; each look's
    // render is then cached).
    val zoomState = rememberZoomState()
    val zoom = zoomState.zoom
    var detailSource by remember { mutableStateOf<PixelImage?>(null) }
    var detail by remember { mutableStateOf<DisplayedFrame?>(null) }
    val detailCache = remember { LruCache<Int, Bitmap>(DetailCacheSize) }

    LaunchedEffect(zoom.isZoomed) {
        if (zoom.isZoomed && detailSource == null) {
            detailSource = withContext(Dispatchers.IO) {
                captureStore.loadPreview(DetailMaxDimension)?.toPixelImage()
            }
        }
    }
    LaunchedEffect(cycle, zoom.isZoomed, detailSource) {
        val source = detailSource
        if (!zoom.isZoomed || source == null) return@LaunchedEffect
        val bitmap = detailCache.get(cycle.position)
            ?: renderPipeline.render(source, cycle.current).toBitmap().also { detailCache.put(cycle.position, it) }
        detail = DisplayedFrame(cycle, bitmap)
    }

    // Full-resolution render + gallery write is slow (CPU LUT interpolation at real photo
    // resolution can take 10s of seconds - see CaptureStore.loadPreview's doc comment for the
    // preview-scale measurement this extrapolates from), so Export needs its own loading state
    // rather than looking hung on the UI thread's behalf.
    var isExporting by remember { mutableStateOf(false) }
    var savedLooks by rememberSaveable(stateSaver = SavedLooksSaver) { mutableStateOf(initialSavedLooks) }
    // Remembered above this screen, so coming back to the Capture later still knows what is saved.
    LaunchedEffect(savedLooks) { onSavedLooksChanged(savedLooks) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val swipeThresholdPx = with(LocalDensity.current) { SwipeThreshold.toPx() }
    // What the shot was taken with, as a camera's playback display shows it.
    val shootingInfo = remember { captureStore.shootingInfo()?.let(::shootingInfoLine) }

    // The sharper render when there is one for the look on screen; otherwise the quick preview.
    val shownBitmap = detail?.takeIf { it.cycle == displayed.cycle }?.bitmap ?: displayed.bitmap

    Box(
        modifier
            .fillMaxSize()
            .zoomable(
                state = zoomState,
                contentSize = { displayed.bitmap?.let { IntSize(it.width, it.height) } ?: IntSize.Zero },
                swipeThresholdPx = swipeThresholdPx,
                // Swiping between looks, at fit only; zoomed, a drag pans instead.
                onSwipe = { toNext -> cycle = if (toNext) cycle.next() else cycle.previous() },
            ),
    ) {
        shownBitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = displayed.cycle.label,
                modifier = Modifier.fillMaxSize().zoomLayer(zoomState),
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
                            selection = selection,
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

            // Back to the Roll with the Capture kept, for deciding later.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 6.dp)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Scrim)
                    .clickable(enabled = !isExporting, onClick = onBack),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Back to the Roll",
                    tint = Paper,
                    modifier = Modifier.size(22.dp),
                )
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

            // Zoomed in on the soft preview while the sharper render is still being made.
            if (zoom.isZoomed && shownBitmap === displayed.bitmap) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(20.dp),
                    strokeWidth = 2.dp,
                )
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
                // On its own dark backing, so it stays readable wherever the photo's edge falls.
                shootingInfo?.let {
                    Text(
                        it,
                        color = Paper.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .background(Scrim, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }

                // A saved look has nothing left to export (its title already carries the
                // checkmark), so Done stands alone, centered, as the one strong button.
                if (isSaved) {
                    PrimaryButton(text = savedLooks.closeActionLabel, enabled = !isExporting, onClick = ::close)
                } else {
                    // Capped so the pair stays together, rather than splitting to the far edges of a
                    // sideways screen.
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.widthIn(max = BottomBarMaxWidth).fillMaxWidth(),
                    ) {
                        SecondaryButton(text = savedLooks.closeActionLabel, enabled = !isExporting, onClick = ::close)
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

/** A quiet companion to [PrimaryButton]: same shape, on a faint translucent pill. */
@Composable
private fun SecondaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Paper.copy(alpha = 0.26f), contentColor = Paper),
    ) {
        Text(text, fontSize = 16.sp)
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
