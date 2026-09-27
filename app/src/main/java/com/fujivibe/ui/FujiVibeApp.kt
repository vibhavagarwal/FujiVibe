package com.fujivibe.ui

import androidx.camera.core.CameraSelector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fujivibe.capture.CaptureStore
import com.fujivibe.gallery.MediaStoreGalleryWriter
import com.fujivibe.ui.review.ReviewScreen
import com.fujivibe.ui.theme.FujiVibeTheme
import com.fujivibe.ui.theme.Ink
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.fujivibe.ui.viewfinder.ViewfinderScreen

private sealed interface Screen {
    data object Viewfinder : Screen
    data object Review : Screen
}

/**
 * Saved so a rotation (Activity recreation) or process death returns to Review rather than
 * silently dropping the Capture, which still sits in [CaptureStore]'s file.
 */
private val ScreenSaver = Saver<Screen, Boolean>(
    save = { it == Screen.Review },
    restore = { inReview -> if (inReview) Screen.Review else Screen.Viewfinder },
)

@Composable
fun FujiVibeApp() {
    val context = LocalContext.current
    val captureStore = remember { CaptureStore(context) }
    val galleryWriter = remember { MediaStoreGalleryWriter(context) }
    var screen by rememberSaveable(stateSaver = ScreenSaver) { mutableStateOf<Screen>(Screen.Viewfinder) }
    // Held here rather than in the Viewfinder so a selfie session stays on the front camera
    // across trips through Review.
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }

    FujiVibeTheme {
        Box(Modifier.fillMaxSize().background(Ink)) {
            when (screen) {
                Screen.Viewfinder -> ViewfinderScreen(
                    captureStore = captureStore,
                    onCaptured = { screen = Screen.Review },
                    lensFacing = lensFacing,
                    onLensFacingChange = { lensFacing = it },
                )
                Screen.Review -> ReviewScreen(
                    captureStore = captureStore,
                    onClose = { screen = Screen.Viewfinder },
                    galleryWriter = galleryWriter,
                )
            }
        }
    }
}
