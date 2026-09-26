package com.fujivibe.ui.viewfinder

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "ExtensionsDiagnostic"

/**
 * THROWAWAY: reports which CameraX vendor extensions (notably BOKEH, i.e. Portrait) the phone's
 * back camera offers, as a small on-screen line plus a Logcat entry, so the answer can be read
 * without a computer. See backlog item 5 (portrait mode). Delete this file, its one call site in
 * ViewfinderScreen, and the camera-extensions dependency once the question is answered.
 */
@Composable
fun ExtensionsDiagnostic(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var report by remember { mutableStateOf("Checking camera extensions...") }

    LaunchedEffect(Unit) {
        report = runCatching { queryExtensions(context) }
            .getOrElse { "Extensions check failed: ${it.message}" }
        Log.i(TAG, report)
    }

    Text(
        text = report,
        color = Color.White,
        fontSize = 11.sp,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private suspend fun queryExtensions(context: Context): String {
    val provider = ProcessCameraProvider.getInstance(context).awaitResult(context)
    val manager = ExtensionsManager.getInstanceAsync(context, provider).awaitResult(context)
    val modes = listOf(
        "BOKEH" to ExtensionMode.BOKEH,
        "HDR" to ExtensionMode.HDR,
        "NIGHT" to ExtensionMode.NIGHT,
        "FACE_RETOUCH" to ExtensionMode.FACE_RETOUCH,
        "AUTO" to ExtensionMode.AUTO,
    )
    val results = modes.joinToString("  ") { (name, mode) ->
        "$name=" + if (manager.isExtensionAvailable(CameraSelector.DEFAULT_BACK_CAMERA, mode)) "yes" else "no"
    }
    return "Extensions (back camera): $results"
}

private suspend fun <T> ListenableFuture<T>.awaitResult(context: Context): T =
    suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }
