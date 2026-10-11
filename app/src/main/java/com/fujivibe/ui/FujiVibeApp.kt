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
import com.fujivibe.ui.review.ReviewScreen
import com.fujivibe.ui.theme.FujiVibeTheme
import com.fujivibe.ui.theme.Ink
import com.fujivibe.ui.viewfinder.ViewfinderScreen

private sealed interface Screen {
    data object Viewfinder : Screen
    data object Review : Screen
    data object Library : Screen

    /** A saved photo, full screen; [photoId] is its MediaStore id. */
    data class Viewer(val photoId: Long) : Screen
}

// MediaStore ids are positive, so the screens without a photo can take the negative numbers.
private const val SAVED_VIEWFINDER = -1L
private const val SAVED_REVIEW = -2L
private const val SAVED_LIBRARY = -3L

/**
 * Saved so a rotation (Activity recreation) or process death returns to Review rather than
 * silently dropping the Capture, which still sits in [CaptureStore]'s file, and to the Library or
 * the photo being viewed rather than bouncing the user back to the Viewfinder.
 */
private val ScreenSaver = Saver<Screen, Long>(
    save = {
        when (it) {
            Screen.Viewfinder -> SAVED_VIEWFINDER
            Screen.Review -> SAVED_REVIEW
            Screen.Library -> SAVED_LIBRARY
            is Screen.Viewer -> it.photoId
        }
    },
    restore = {
        when {
            it >= 0 -> Screen.Viewer(it)
            it == SAVED_REVIEW -> Screen.Review
            it == SAVED_LIBRARY -> Screen.Library
            else -> Screen.Viewfinder
        }
    },
)

@Composable
fun FujiVibeApp() {
    val context = LocalContext.current
    val captureStore = remember { CaptureStore(context) }
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
                    onCaptured = { screen = Screen.Review },
                    onOpenLibrary = { screen = Screen.Library },
                    lensFacing = lensFacing,
                    onLensFacingChange = { lensFacing = it },
                )
                Screen.Review -> ReviewScreen(
                    captureStore = captureStore,
                    onClose = { screen = Screen.Viewfinder },
                    galleryWriter = galleryWriter,
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
