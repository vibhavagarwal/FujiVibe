package com.fujivibe.ui

import androidx.camera.core.CameraSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fujivibe.capture.CaptureStore
import com.fujivibe.gallery.MediaStoreGalleryWriter
import com.fujivibe.library.MediaStoreLibraryReader
import com.fujivibe.ui.library.LibraryModel
import com.fujivibe.ui.library.LibraryScreen
import com.fujivibe.ui.library.ViewerScreen
import com.fujivibe.review.SavedLooks
import com.fujivibe.ui.review.ReviewScreen
import com.fujivibe.ui.roll.RollScreen
import com.fujivibe.ui.theme.FujiVibeTheme
import com.fujivibe.ui.theme.Ink
import com.fujivibe.ui.viewfinder.ViewfinderScreen

private sealed interface Screen {
    data object Viewfinder : Screen
    data object Roll : Screen

    /** A Capture from the Roll being developed; [captureId] is the time it was taken. */
    data class Review(val captureId: Long) : Screen
    data object Library : Screen

    /** A saved photo, full screen; [photoId] is its MediaStore id. */
    data class Viewer(val photoId: Long) : Screen
}

/**
 * Saved so a rotation (Activity recreation) or process death returns to the Capture being
 * developed, which still sits on the Roll, and to the Library or the photo being viewed, rather
 * than bouncing the user back to the Viewfinder.
 */
private val ScreenSaver = Saver<Screen, String>(
    save = {
        when (it) {
            Screen.Viewfinder -> "viewfinder"
            Screen.Roll -> "roll"
            Screen.Library -> "library"
            is Screen.Review -> "review:${it.captureId}"
            is Screen.Viewer -> "viewer:${it.photoId}"
        }
    },
    restore = {
        val id = it.substringAfter(':', "").toLongOrNull()
        when {
            it == "roll" -> Screen.Roll
            it == "library" -> Screen.Library
            it.startsWith("review:") && id != null -> Screen.Review(id)
            it.startsWith("viewer:") && id != null -> Screen.Viewer(id)
            else -> Screen.Viewfinder
        }
    },
)

@Composable
fun FujiVibeApp() {
    val context = LocalContext.current
    val captureStore = remember { CaptureStore(context) }
    // The Captures waiting to be developed. Shots past their 30 days are cleared as the app opens.
    var rollIds by remember { captureStore.purgeExpired(); mutableStateOf(captureStore.ids()) }
    // What has been Exported from each Capture so far, kept while its Review is left and
    // reopened, so a look isn't saved twice.
    // Where leaving Review goes: the Roll if it was opened from there, the Viewfinder if straight
    // from the shutter. Not saved; after a restart it falls back to the Roll.
    var reviewReturnsToRoll by remember { mutableStateOf(true) }
    val savedLooksById = remember { mutableMapOf<Long, SavedLooks>() }
    val galleryWriter = remember { MediaStoreGalleryWriter(context) }
    // Held here, above the screens, so the grid and the viewer share one list, and the grid keeps
    // its scroll position while a photo is open.
    val libraryModel = remember { LibraryModel(MediaStoreLibraryReader(context)) }
    val libraryGridState = rememberLazyGridState()
    var libraryFocusId by remember { mutableStateOf<Long?>(null) }
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Viewfinder) }
    // Held here rather than in the Viewfinder so a selfie session stays on the front camera
    // across trips through Review.
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }

    FujiVibeTheme {
        Box(Modifier.fillMaxSize().background(Ink)) {
            when (val current = screen) {
                Screen.Viewfinder -> ViewfinderScreen(
                    captureStore = captureStore,
                    rollIds = rollIds,
                    onCaptured = { captureId, developNow ->
                        rollIds = captureStore.ids()
                        if (developNow) {
                            reviewReturnsToRoll = false
                            screen = Screen.Review(captureId)
                        }
                    },
                    onOpenRoll = { screen = Screen.Roll },
                    onOpenLibrary = { screen = Screen.Library },
                    lensFacing = lensFacing,
                    onLensFacingChange = { lensFacing = it },
                )
                Screen.Roll -> RollScreen(
                    captureStore = captureStore,
                    ids = rollIds,
                    onOpen = {
                        reviewReturnsToRoll = true
                        screen = Screen.Review(it)
                    },
                    onBack = { screen = Screen.Viewfinder },
                )
                is Screen.Review -> ReviewScreen(
                    captureStore = remember(current.captureId) { captureStore.open(current.captureId) },
                    onClose = {
                        // Done or Discard: the Capture is gone, so on to the next one waiting.
                        savedLooksById.remove(current.captureId)
                        rollIds = captureStore.ids()
                        screen = if (reviewReturnsToRoll) Screen.Roll else Screen.Viewfinder
                    },
                    onBack = { screen = if (reviewReturnsToRoll) Screen.Roll else Screen.Viewfinder },
                    galleryWriter = galleryWriter,
                    initialSavedLooks = savedLooksById[current.captureId] ?: SavedLooks.NONE,
                    onSavedLooksChanged = { savedLooksById[current.captureId] = it },
                )
                Screen.Library -> LibraryScreen(
                    model = libraryModel,
                    gridState = libraryGridState,
                    focusPhotoId = libraryFocusId,
                    onOpenPhoto = { screen = Screen.Viewer(it) },
                    onBack = { screen = Screen.Viewfinder },
                )
                is Screen.Viewer -> {
                    // After a rotation the list is gone with the rest of this screen's state; reload it.
                    LaunchedEffect(Unit) { if (libraryModel.photos == null) libraryModel.refresh() }
                    ViewerScreen(
                        photos = libraryModel.photos,
                        startPhotoId = current.photoId,
                        onPhotoChanged = { screen = Screen.Viewer(it) },
                        onBack = {
                            libraryFocusId = it
                            screen = Screen.Library
                        },
                        onDeleted = { deletedId, nextId ->
                            // Both changes land together, so the viewer never sees a photo that is
                            // gone while it is still being asked to show it.
                            libraryModel.remove(deletedId)
                            if (nextId != null) {
                                screen = Screen.Viewer(nextId)
                            } else {
                                libraryFocusId = null
                                screen = Screen.Library
                            }
                        },
                    )
                }
            }
        }
    }
}
