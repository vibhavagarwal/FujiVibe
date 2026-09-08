package com.fujivibe.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.fujivibe.capture.CaptureStore
import com.fujivibe.ui.review.ReviewScreen
import com.fujivibe.ui.viewfinder.ViewfinderScreen

private sealed interface Screen {
    data object Viewfinder : Screen
    data object Review : Screen
}

@Composable
fun FujiVibeApp() {
    val captureStore = remember { CaptureStore(LocalContext.current) }
    var screen by remember { mutableStateOf<Screen>(Screen.Viewfinder) }

    MaterialTheme {
        when (screen) {
            Screen.Viewfinder -> ViewfinderScreen(
                captureStore = captureStore,
                onCaptured = { screen = Screen.Review },
            )
            Screen.Review -> ReviewScreen(
                captureStore = captureStore,
                onDiscard = { screen = Screen.Viewfinder },
            )
        }
    }
}
