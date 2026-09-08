package com.fujivibe.ui.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.fujivibe.capture.CaptureStore

/**
 * Shows the current Capture as Original (no Film Simulation applied — swiping
 * between looks lands in a later ticket) with a Discard control.
 */
@Composable
fun ReviewScreen(
    captureStore: CaptureStore,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember { captureStore.loadFullResolution() }

    Box(modifier.fillMaxSize()) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Original",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        Text(
            text = "Original",
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
        )

        Button(
            onClick = {
                captureStore.discard()
                onDiscard()
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
        ) {
            Text("Discard")
        }
    }
}
