package com.fujivibe.ui.viewfinder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fujivibe.capture.CaptureStore
import com.fujivibe.viewfinder.pinchedZoomRatio
import com.fujivibe.viewfinder.sliderFractionToZoom
import com.fujivibe.viewfinder.zoomToSliderFraction
import kotlinx.coroutines.delay
import com.fujivibe.viewfinder.zoomReadout

private const val TAG = "ViewfinderScreen"

/** How long the zoom slider stays visible after the last zoom interaction. */
private const val SLIDER_IDLE_MS = 2500L

/**
 * Live camera preview with a single shutter control. Fully isolated from
 * LUT/processing logic — no processing code runs on this screen or its
 * capture path.
 */
@Composable
fun ViewfinderScreen(
    captureStore: CaptureStore,
    onCaptured: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun cameraPermissionGranted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    var hasCameraPermission by remember { mutableStateOf(cameraPermissionGranted()) }

    // Re-check on every resume so granting the permission in system Settings and coming back
    // takes effect without restarting the app.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasCameraPermission = cameraPermissionGranted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        CameraPermissionRequired(
            onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            // After a permanent denial the system no longer shows the request dialog, so app
            // Settings is the only way back.
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = modifier,
        )
        return
    }

    // Locked to the same aspect ratio as the saved capture (below) so what's
    // framed in the preview matches what actually gets exported — otherwise
    // a full-screen preview crops more of the frame than the photo does.
    val imageCapture = remember {
        ImageCapture.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
    }
    var isCapturing by remember { mutableStateOf(false) }
    // Real camera zoom (CameraControl), so the Capture itself is zoomed - not a preview-only crop.
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableStateOf(1f) }
    // The slider appears while zooming and fades away after a short idle; each zoom interaction
    // bumps the tick, which restarts the timer.
    var sliderVisible by remember { mutableStateOf(false) }
    var zoomInteractions by remember { mutableIntStateOf(0) }
    LaunchedEffect(zoomInteractions) {
        if (zoomInteractions == 0) return@LaunchedEffect
        sliderVisible = true
        delay(SLIDER_IDLE_MS)
        sliderVisible = false
    }

    fun applyZoom(ratio: Float) {
        val boundCamera = camera ?: return
        zoomRatio = ratio
        boundCamera.cameraControl.setZoomRatio(ratio)
        zoomInteractions++
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    // FILL_CENTER (the default) crops the preview to the
                    // screen's non-4:3 aspect ratio, hiding part of the frame
                    // that the 4:3 capture still saves in full. FIT_CENTER
                    // shows the whole framed area the photo will contain.
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
                val scaleDetector = ScaleGestureDetector(
                    ctx,
                    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        override fun onScale(detector: ScaleGestureDetector): Boolean {
                            val boundCamera = camera ?: return false
                            val zoomState = boundCamera.cameraInfo.zoomState.value ?: return false
                            applyZoom(
                                pinchedZoomRatio(
                                    current = zoomRatio,
                                    scaleFactor = detector.scaleFactor,
                                    min = zoomState.minZoomRatio,
                                    max = zoomState.maxZoomRatio,
                                )
                            )
                            return true
                        }
                    },
                )
                previewView.setOnTouchListener { view, event ->
                    scaleDetector.onTouchEvent(event)
                    if (event.action == MotionEvent.ACTION_UP) view.performClick()
                    true
                }
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder()
                            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                            .build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        ).also {
                            // CameraX keeps the last zoom on the Camera across unbind/rebind, so
                            // coming back from Review would otherwise start at the old zoom.
                            it.cameraControl.setZoomRatio(1f)
                            zoomRatio = 1f
                        }
                    },
                    ContextCompat.getMainExecutor(ctx),
                )
                previewView
            },
        )

        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            ExtensionsDiagnostic(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
            ) {
                val zoomState = camera?.cameraInfo?.zoomState?.value
                val minZoom = zoomState?.minZoomRatio ?: 1f
                val maxZoom = zoomState?.maxZoomRatio ?: 1f
                if (sliderVisible && maxZoom > minZoom) {
                    Slider(
                        value = zoomToSliderFraction(zoomRatio, minZoom, maxZoom),
                        onValueChange = { applyZoom(sliderFractionToZoom(it, minZoom, maxZoom)) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.35f),
                        ),
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                }
                Text(
                    text = zoomReadout(zoomRatio),
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable { zoomInteractions++ }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(enabled = !isCapturing) {
                        isCapturing = true
                        val outputFile = captureStore.fileForNewCapture()
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    isCapturing = false
                                    onCaptured()
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    Log.e(TAG, "Capture failed", exception)
                                }
                            },
                        )
                    },
            )
        }
    }
}

@Composable
private fun CameraPermissionRequired(
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().safeDrawingPadding().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "FujiVibe needs the camera to take photos. If you denied it before, you may need to allow it in Settings.",
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGrant) { Text("Grant permission") }
            OutlinedButton(onClick = onOpenSettings) { Text("Open Settings") }
        }
    }
}
