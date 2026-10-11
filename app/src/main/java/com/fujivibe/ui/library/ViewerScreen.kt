package com.fujivibe.ui.library

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import com.fujivibe.R
import com.fujivibe.capture.shootingInfoLine
import com.fujivibe.capture.toShootingInfo
import com.fujivibe.library.LibraryPhoto
import com.fujivibe.library.decodeSampleSize
import com.fujivibe.library.libraryPhotoUri
import com.fujivibe.library.photoAfterDelete
import com.fujivibe.library.positionLabel
import com.fujivibe.library.startPage
import com.fujivibe.ui.theme.Paper
import com.fujivibe.ui.theme.PaperMuted
import com.fujivibe.ui.theme.Scrim
import com.fujivibe.ui.zoom.rememberZoomState
import com.fujivibe.ui.zoom.zoomLayer
import com.fujivibe.ui.zoom.zoomable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Longest edge the viewer decodes a saved photo at. The phone's own Exports are about 4000px, so
 * they open at full size and can be zoomed to a true 100%; anything bigger is halved to fit.
 */
private const val MaxDecodeEdge = 4096

/**
 * One saved photo at a time, full screen, with swiping between photos and the same pinch-zoom as
 * Review. [photos] is null while the list is still loading. [onPhotoChanged] reports the photo now
 * shown (so a rotation reopens on it), and [onBack] reports the one the user was last looking at
 * (so the grid can scroll to it). [onDeleted] is told which photo was just deleted and which to
 * show next (null when none is left).
 */
@Composable
fun ViewerScreen(
    photos: List<LibraryPhoto>?,
    startPhotoId: Long,
    onPhotoChanged: (Long) -> Unit,
    onBack: (Long) -> Unit,
    onDeleted: (deletedId: Long, nextId: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBack(startPhotoId) }

    if (photos == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val start = startPage(photos, startPhotoId)
    // The photo is no longer in the Library (deleted elsewhere): nothing to show.
    if (start == null) {
        LaunchedEffect(Unit) { onBack(startPhotoId) }
        return
    }

    val latestPhotos by rememberUpdatedState(photos)
    val pagerState = rememberPagerState(initialPage = start) { latestPhotos.size }
    val currentPhoto = photos.getOrNull(pagerState.currentPage)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            latestPhotos.getOrNull(page)?.let { onPhotoChanged(it.id) }
        }
    }

    // Whether the photo on screen is zoomed in; the surrounding controls step aside when it is.
    var zoomed by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val infoLine by produceState<String?>(initialValue = null, currentPhoto?.id) {
        value = currentPhoto?.let { withContext(Dispatchers.IO) { readShootingInfoLine(context, it) } }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // The photo a delete is waiting on (Android's confirmation is up, so a rotation mustn't forget it).
    var pendingDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Android 10 only: it has no system delete request, so we ask ourselves and delete directly.
    var confirmDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun deleteFailed() {
        scope.launch { snackbarHostState.showSnackbar("Couldn't delete this photo") }
    }

    val deleteRequestLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val deletedId = pendingDeleteId
        pendingDeleteId = null
        // Cancelled in Android's dialog: the photo stays and there is nothing to report.
        if (deletedId != null && result.resultCode == Activity.RESULT_OK) {
            onDeleted(deletedId, photoAfterDelete(latestPhotos, deletedId))
        }
    }

    fun requestDelete(photo: LibraryPhoto) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android's own confirmation, and the photo goes to its trash (restorable for a while)
            // rather than vanishing. It also works for photos another app (Google Photos) now owns,
            // which a plain delete is refused for.
            pendingDeleteId = photo.id
            try {
                val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(libraryPhotoUri(photo.id)))
                deleteRequestLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } catch (e: Exception) {
                pendingDeleteId = null
                deleteFailed()
            }
        } else {
            confirmDeleteId = photo.id
        }
    }

    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("Delete this photo?") },
            text = { Text("It will be removed from your phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteId = null
                    scope.launch {
                        val deleted = withContext(Dispatchers.IO) {
                            try {
                                context.contentResolver.delete(libraryPhotoUri(id), null, null) > 0
                            } catch (e: SecurityException) {
                                false
                            }
                        }
                        if (deleted) onDeleted(id, photoAfterDelete(latestPhotos, id)) else deleteFailed()
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteId = null }) { Text("Cancel") } },
        )
    }

    Box(modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            key = { latestPhotos.getOrNull(it)?.id ?: it },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            latestPhotos.getOrNull(page)?.let { photo ->
                ViewerPage(
                    photo = photo,
                    isCurrent = page == pagerState.settledPage,
                    onZoomedChange = { zoomed = it },
                )
            }
        }

        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Scrim)
                        .clickable { currentPhoto?.let { onBack(it.id) } ?: onBack(startPhotoId) },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = Paper,
                        modifier = Modifier.size(22.dp),
                    )
                }
                AnimatedVisibility(visible = !zoomed, modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        currentPhoto?.lookLabel?.let {
                            Text(
                                it,
                                color = Paper,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                        Box(Modifier.weight(1f))
                        Text(
                            positionLabel(pagerState.currentPage, photos.size),
                            color = PaperMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Scrim)
                                .clickable { currentPhoto?.let(::requestDelete) },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete),
                                contentDescription = "Delete",
                                tint = Paper,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
            )

            AnimatedVisibility(visible = !zoomed && infoLine != null, modifier = Modifier.align(Alignment.BottomCenter)) {
                Text(
                    infoLine.orEmpty(),
                    color = Paper.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .background(Scrim, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
    }
}

/** One photo, decoded at full size and zoomable. Its zoom is its own, and resets when it scrolls out of the pager. */
@Composable
private fun ViewerPage(photo: LibraryPhoto, isCurrent: Boolean, onZoomedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val zoomState = rememberZoomState()
    var bitmap by remember(photo.id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(photo.id) { mutableStateOf(false) }

    LaunchedEffect(photo.id) {
        val decoded = withContext(Dispatchers.IO) { decodePhoto(context, photo.id) }
        bitmap = decoded
        failed = decoded == null
    }
    LaunchedEffect(isCurrent, zoomState.zoom.isZoomed) {
        if (isCurrent) onZoomedChange(zoomState.zoom.isZoomed)
    }

    Box(
        Modifier
            .fillMaxSize()
            .zoomable(zoomState, contentSize = { bitmap?.let { IntSize(it.width, it.height) } ?: IntSize.Zero }),
        contentAlignment = Alignment.Center,
    ) {
        val shown = bitmap
        when {
            shown != null -> Image(
                bitmap = shown.asImageBitmap(),
                contentDescription = photo.lookLabel ?: photo.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().zoomLayer(zoomState),
            )
            failed -> Text("Couldn't open this photo", color = PaperMuted, fontSize = 16.sp)
            else -> CircularProgressIndicator()
        }
    }
}

/** The saved JPEG, upright (the decoder applies its orientation) and at most [MaxDecodeEdge] on its long side. */
private fun decodePhoto(context: Context, id: Long): Bitmap? = try {
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, libraryPhotoUri(id))) { decoder, info, _ ->
        decoder.setTargetSampleSize(decodeSampleSize(maxOf(info.size.width, info.size.height), MaxDecodeEdge))
    }
} catch (e: IOException) {
    null
} catch (e: SecurityException) {
    null
}

/** "ISO 400 · 1/250 · 3:2" for a saved photo, read from the camera details Export copied across; null if there are none. */
private fun readShootingInfoLine(context: Context, photo: LibraryPhoto): String? = try {
    val uri = libraryPhotoUri(photo.id)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        ExifInterface(stream).toShootingInfo(width = bounds.outWidth, height = bounds.outHeight)
            .let(::shootingInfoLine)
            .takeIf { it.isNotBlank() }
    }
} catch (e: IOException) {
    null
} catch (e: SecurityException) {
    null
}
