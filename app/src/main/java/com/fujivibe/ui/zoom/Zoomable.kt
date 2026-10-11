package com.fujivibe.ui.zoom

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.fujivibe.review.ReviewZoom
import com.fujivibe.review.fittedSize
import kotlin.math.abs
import kotlin.math.hypot

/** The zoom and pan of one photo, plus the size of the view it is shown in (needed to keep it on screen). */
@Stable
class ZoomState {
    var zoom by mutableStateOf(ReviewZoom.FIT)
    var viewSize by mutableStateOf(IntSize.Zero)
}

@Composable
fun rememberZoomState(): ZoomState = remember { ZoomState() }

/**
 * Pinch-zoom, pan and double-tap on a photo, shared by Review and the Library viewer. One handler
 * takes every one-finger and two-finger gesture, so they can't fight: a pinch always zooms and
 * pans; a one-finger drag pans while zoomed; a double-tap toggles zoom. Pair it with [zoomLayer] on
 * the image itself.
 *
 * [contentSize] is the photo's pixel size (zero while there is none), used to work out where its
 * edges are. [onSwipe], if given, is called with `true` for a swipe left (towards the next item) or
 * `false` for a swipe right, only when the photo is not zoomed. Left null, a one-finger drag at fit
 * is left unconsumed, so a parent (the viewer's pager) can take it.
 */
fun Modifier.zoomable(
    state: ZoomState,
    contentSize: () -> IntSize,
    swipeThresholdPx: Float = 0f,
    onSwipe: ((toNext: Boolean) -> Unit)? = null,
): Modifier = composed {
    val currentContentSize by rememberUpdatedState(contentSize)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    this
        .onSizeChanged { state.viewSize = it }
        .pointerInput(state, swipeThresholdPx) {
            var lastTapUptime = 0L
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val startedZoomed = state.zoom.isZoomed
                // A tap on a button drawn over the photo is the button's, not a double-tap.
                val startedOnControl = down.isConsumed
                var pinched = false
                var travelX = 0f
                var travelY = 0f
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.count { it.pressed } >= 2) pinched = true
                    val pan = event.calculatePan()
                    if (!pinched) {
                        travelX += pan.x
                        travelY += pan.y
                    }
                    val content = currentContentSize()
                    // No finger left down (the lift event) has no centroid: it comes back
                    // unspecified, i.e. NaN, so there is nothing to transform.
                    val centroid = event.calculateCentroid()
                    if ((pinched || state.zoom.isZoomed) && content.width > 0 && centroid.isSpecified) {
                        val viewWidth = state.viewSize.width.toFloat()
                        val viewHeight = state.viewSize.height.toFloat()
                        state.zoom = state.zoom.transformedBy(
                            zoomChange = event.calculateZoom(),
                            panX = pan.x,
                            panY = pan.y,
                            centroidX = centroid.x,
                            centroidY = centroid.y,
                            viewWidth = viewWidth,
                            viewHeight = viewHeight,
                            content = fittedSize(viewWidth, viewHeight, content.width, content.height),
                        )
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })

                if (pinched) {
                    lastTapUptime = 0L
                    return@awaitEachGesture
                }
                val content = currentContentSize()
                val isTap = !startedOnControl && hypot(travelX, travelY) < viewConfiguration.touchSlop
                val swipe = currentOnSwipe
                when {
                    isTap && content.width > 0 -> {
                        if (down.uptimeMillis - lastTapUptime <= viewConfiguration.doubleTapTimeoutMillis) {
                            val viewWidth = state.viewSize.width.toFloat()
                            val viewHeight = state.viewSize.height.toFloat()
                            state.zoom = state.zoom.doubleTapped(
                                tapX = down.position.x,
                                tapY = down.position.y,
                                viewWidth = viewWidth,
                                viewHeight = viewHeight,
                                content = fittedSize(viewWidth, viewHeight, content.width, content.height),
                            )
                            lastTapUptime = 0L
                        } else {
                            lastTapUptime = down.uptimeMillis
                        }
                    }
                    // Swiping to another item is for the fitted photo only; zoomed, a drag pans.
                    swipe != null && !startedZoomed && abs(travelX) >= swipeThresholdPx && abs(travelX) > abs(travelY) ->
                        swipe(travelX < 0)
                }
            }
        }
}

/** Applies [state]'s zoom and pan to the image it is placed on. */
fun Modifier.zoomLayer(state: ZoomState): Modifier = graphicsLayer {
    scaleX = state.zoom.scale
    scaleY = state.zoom.scale
    translationX = state.zoom.offsetX
    translationY = state.zoom.offsetY
}
