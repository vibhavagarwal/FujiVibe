package com.fujivibe.ui.library

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fujivibe.R
import com.fujivibe.library.LibraryPhoto
import com.fujivibe.library.libraryPhotoUri
import com.fujivibe.library.libraryReadPermission
import com.fujivibe.ui.theme.Paper
import com.fujivibe.ui.theme.PaperMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Edge, in px, of the square thumbnails requested from MediaStore. */
private const val ThumbnailSizePx = 320

/**
 * The Library: the photos FujiVibe has Exported, newest first, each marked with the look it was
 * saved with. Reading photos another app has since taken over needs the media permission, so
 * without it a banner offers to grant it and the grid shows only what the app can see.
 */
@Composable
fun LibraryScreen(
    model: LibraryModel,
    gridState: LazyGridState,
    focusPhotoId: Long?,
    onOpenPhoto: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permission = libraryReadPermission(Build.VERSION.SDK_INT)

    fun accessGranted() = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    var hasAccess by remember { mutableStateOf(accessGranted()) }
    val photos = model.photos

    // Re-check on every resume so granting access in system Settings and coming back takes effect.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasAccess = accessGranted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Reloaded whenever access changes, since that changes which photos are visible.
    LaunchedEffect(hasAccess) { model.refresh() }

    // Coming back from the viewer: if swiping there moved on to a photo that isn't on screen, bring
    // it into view. The grid state itself lives above this screen, so it otherwise stays put.
    LaunchedEffect(photos, focusPhotoId) {
        val index = photos?.indexOfFirst { it.id == focusPhotoId } ?: -1
        if (index >= 0 && gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            gridState.scrollToItem(index)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasAccess = it
    }

    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        Header(count = photos?.size, onBack = onBack)

        if (!hasAccess) {
            AccessBanner(
                onAllow = { permissionLauncher.launch(permission) },
                // After a permanent denial the system no longer shows the request dialog, so the
                // only way left to grant access is the app's page in Settings.
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
            )
        }

        val shown = photos
        when {
            shown == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            shown.isEmpty() -> if (hasAccess) EmptyState()
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { PhotoTile(it, onClick = { onOpenPhoto(it.id) }) }
            }
        }
    }
}

@Composable
private fun Header(count: Int?, onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onBack),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "Back",
                tint = Paper,
                modifier = Modifier.size(22.dp),
            )
        }
        Text("Library", color = Paper, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
        Box(Modifier.weight(1f))
        if (count != null && count > 0) {
            Text(
                if (count == 1) "1 photo" else "$count photos",
                color = PaperMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun AccessBanner(onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(
            "Allow access to photos to see everything you've saved. Without it, only some photos show here.",
            color = Paper,
            fontSize = 14.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAllow) { Text("Allow") }
            OutlinedButton(onClick = onOpenSettings) { Text("Open Settings") }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().padding(32.dp),
    ) {
        Text("No photos yet", color = Paper, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text(
            "Photos you Export appear here.",
            color = PaperMuted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** A square thumbnail with the look's name in a corner (nothing for older photos that never recorded one). */
@Composable
private fun PhotoTile(photo: LibraryPhoto, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1E1E1E))
            .clickable(onClick = onClick)
    ) {
        Thumbnail(photo, Modifier.fillMaxSize())
        // An acronym with a soft shadow rather than a backing pill, so it hides as little of the
        // photo as possible while still reading against light or dark.
        photo.lookShortLabel?.let {
            Text(
                it,
                color = Paper,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.85f), Offset(0f, 1f), blurRadius = 6f)),
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 7.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun Thumbnail(photo: LibraryPhoto, modifier: Modifier) {
    val context = LocalContext.current
    var thumbnail by remember(photo.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(photo.id) {
        thumbnail = withContext(Dispatchers.IO) {
            try {
                context.contentResolver
                    .loadThumbnail(libraryPhotoUri(photo.id), Size(ThumbnailSizePx, ThumbnailSizePx), null)
                    .asImageBitmap()
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            }
        }
    }

    thumbnail?.let {
        Image(
            bitmap = it,
            contentDescription = photo.lookLabel ?: photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}
