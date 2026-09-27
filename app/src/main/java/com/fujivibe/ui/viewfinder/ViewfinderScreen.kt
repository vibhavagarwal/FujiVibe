package com.fujivibe.ui.viewfinder

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.platform.LocalView
import com.fujivibe.ui.theme.Amber
import com.fujivibe.ui.theme.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.util.Rational
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.fujivibe.viewfinder.brightnessReadout
import com.fujivibe.viewfinder.exposureOffsetStops
import kotlin.math.abs
import kotlin.math.ln
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.fujivibe.capture.CaptureStore
import com.fujivibe.viewfinder.AspectChoice
import com.fujivibe.viewfinder.TimerChoice
import com.fujivibe.viewfinder.ViewfinderSettings
import com.fujivibe.viewfinder.horizonAngle
import com.fujivibe.viewfinder.Exposure
import com.fujivibe.viewfinder.MAX_MANUAL_EXPOSURE_NS
import com.fujivibe.viewfinder.isoReadout
import com.fujivibe.viewfinder.isoStops
import com.fujivibe.viewfinder.pinchedZoomRatio
import com.fujivibe.viewfinder.resolveExposure
import com.fujivibe.viewfinder.shutterReadout
import com.fujivibe.viewfinder.shutterStops
import com.fujivibe.viewfinder.sliderFractionToZoom
import com.fujivibe.viewfinder.zoomReadout
import com.fujivibe.viewfinder.zoomToSliderFraction
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "ViewfinderScreen"

/** How long the control panel stays open after the last zoom/ISO/shutter interaction. */
private const val DIAL_IDLE_MS = 3000L

/** Manual frames run no faster than 30fps, and no faster than their own exposure allows. */
private const val MIN_FRAME_DURATION_NS = 33_333_333L

/** Used only if manual mode starts before the camera has reported a single metered frame. */
private val FALLBACK_METERED = Exposure(iso = 100, exposureTimeNs = 10_000_000L)

/** Firm click for the shutter; CONFIRM needs Android 11, older phones get a key-press tap. */
private val SHUTTER_HAPTIC =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY

/** Which setting the shared dial is driving. */
private enum class Dial { ZOOM, ISO, SHUTTER }

/** What the bound camera allows for manual exposure; null (below) when it allows none. */
private class ManualExposureSupport(
    val isoRange: IntRange,
    val exposureTimeRangeNs: LongRange,
    val isoStops: List<Int>,
    val shutterStops: List<Long>,
)

/**
 * Remembers the camera's own automatic exposure from each preview frame, so a manual ISO (or
 * shutter) can be paired with a shutter (or ISO) that keeps the same brightness. Frozen while
 * any manual value is set, since those frames no longer reflect the camera's metering.
 */
private class ExposureMeter {
    @Volatile var tracking = true
    @Volatile var latest: Exposure? = null

    val callback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            if (!tracking) return
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
            val time = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
            latest = Exposure(iso, time)
        }
    }
}

/**
 * Live camera preview with a shutter, a back/selfie switch, and a fold-away control panel
 * (zoom, ISO, shutter speed) sharing one ruler. Fully isolated from LUT/processing logic — no processing code runs on this
 * screen or its capture path.
 */
@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun ViewfinderScreen(
    captureStore: CaptureStore,
    onCaptured: () -> Unit,
    lensFacing: Int,
    onLensFacingChange: (Int) -> Unit,
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

    // Grid, level, timer and aspect ratio persist across launches; everything else resets.
    val settings = remember { ViewfinderSettings(context) }
    var gridOn by remember { mutableStateOf(settings.gridOn) }
    var levelOn by remember { mutableStateOf(settings.levelOn) }
    var timer by remember { mutableStateOf(settings.timer) }
    var aspect by remember { mutableStateOf(settings.aspect) }

    // Rebuilt on every bind, since its stream shape follows the chosen aspect ratio.
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var countdown by remember { mutableStateOf<Int?>(null) }
    var countdownJob by remember { mutableStateOf<Job?>(null) }
    val view = LocalView.current
    val shutterBlink = remember { Animatable(0f) }

    // Level line: the horizon's on-screen angle from the gravity sensor, only while switched on.
    var horizon by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(levelOn) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rotation = when (ContextCompat.getDisplayOrDefault(context).rotation) {
                    Surface.ROTATION_90 -> 90
                    Surface.ROTATION_180 -> 180
                    Surface.ROTATION_270 -> 270
                    else -> 0
                }
                horizon = horizonAngle(event.values[0], event.values[1], event.values[2], rotation)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (levelOn && sensor != null) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sensorManager.unregisterListener(listener)
            horizon = null
        }
    }
    // Real camera zoom (CameraControl), so the Capture itself is zoomed - not a preview-only crop.
    var camera by remember { mutableStateOf<Camera?>(null) }
    var hasFrontCamera by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableStateOf(1f) }
    // null = Auto. Applied through Camera2 interop, so they change the Capture, not just the preview.
    var manualSupport by remember { mutableStateOf<ManualExposureSupport?>(null) }
    var isoChoice by remember { mutableStateOf<Int?>(null) }
    var shutterChoiceNs by remember { mutableStateOf<Long?>(null) }
    val meter = remember { ExposureMeter() }
    // The dial appears on a zoom/ISO/shutter interaction and fades away after a short idle; each
    // interaction bumps the tick, which restarts the timer.
    var activeDial by remember { mutableStateOf(Dial.ZOOM) }
    var dialVisible by remember { mutableStateOf(false) }
    var dialInteractions by remember { mutableIntStateOf(0) }
    LaunchedEffect(dialInteractions) {
        if (dialInteractions == 0) return@LaunchedEffect
        dialVisible = true
        delay(DIAL_IDLE_MS)
        dialVisible = false
    }

    fun showDial(dial: Dial) {
        activeDial = dial
        dialInteractions++
    }

    /** Restarts the fold-away timer while the user is still adjusting. */
    fun keepDialOpen() = showDial(activeDial)

    /** Tapping a tab opens its ruler; tapping the open tab again folds the panel. */
    fun toggleDial(dial: Dial) {
        if (dialVisible && activeDial == dial) dialVisible = false else showDial(dial)
    }

    fun applyZoom(ratio: Float) {
        val boundCamera = camera ?: return
        zoomRatio = ratio
        boundCamera.cameraControl.setZoomRatio(ratio)
        showDial(Dial.ZOOM)
    }

    fun currentExposure(support: ManualExposureSupport): Exposure? = resolveExposure(
        isoChoice = isoChoice,
        shutterChoiceNs = shutterChoiceNs,
        metered = meter.latest ?: FALLBACK_METERED,
        isoRange = support.isoRange,
        exposureTimeRangeNs = support.exposureTimeRangeNs,
    )

    fun applyExposure() {
        val boundCamera = camera ?: return
        val support = manualSupport ?: return
        val control = Camera2CameraControl.from(boundCamera.cameraControl)
        val exposure = currentExposure(support)
        if (exposure == null) {
            meter.tracking = true
            control.clearCaptureRequestOptions()
            return
        }
        meter.tracking = false
        control.setCaptureRequestOptions(
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, exposure.iso)
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure.exposureTimeNs)
                .setCaptureRequestOption(
                    CaptureRequest.SENSOR_FRAME_DURATION,
                    maxOf(exposure.exposureTimeNs, MIN_FRAME_DURATION_NS),
                )
                .build()
        )
    }

    val previewView = remember {
        PreviewView(context).apply {
            // FILL_CENTER (the default) crops the preview to the
            // screen's non-4:3 aspect ratio, hiding part of the frame
            // that the 4:3 capture still saves in full. FIT_CENTER
            // shows the whole framed area the photo will contain.
            scaleType = PreviewView.ScaleType.FIT_CENTER
            val scaleDetector = ScaleGestureDetector(
                context,
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
            setOnTouchListener { view, event ->
                scaleDetector.onTouchEvent(event)
                if (event.action == MotionEvent.ACTION_UP) view.performClick()
                true
            }
        }
    }

    // (Re)binds whenever the chosen camera or aspect ratio changes. Every bind starts clean: zoom
    // 1x, ISO and shutter on Auto — CameraX would otherwise carry the last zoom and Camera2
    // options over.
    LaunchedEffect(lensFacing, aspect) {
        val cameraProvider = awaitCameraProvider(context)
        hasFrontCamera = cameraProvider.hasCameraSafely(CameraSelector.DEFAULT_FRONT_CAMERA)
        val requested = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val selector = if (cameraProvider.hasCameraSafely(requested)) requested else CameraSelector.DEFAULT_BACK_CAMERA

        // Preview and Capture share one stream shape so what's framed is what's saved: 16:9 uses
        // the sensor's 16:9 mode, everything else its full 4:3. 3:2 and 1:1 are then cropped
        // from 4:3 by a ViewPort, which CameraX applies to the preview and the saved JPEG alike.
        val streamRatio = if (aspect == AspectChoice.SIXTEEN_NINE) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3
        val previewBuilder = Preview.Builder().setTargetAspectRatio(streamRatio)
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(meter.callback)
        val preview = previewBuilder.build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val capture = ImageCapture.Builder().setTargetAspectRatio(streamRatio).build()
        val useCases = UseCaseGroup.Builder().addUseCase(preview).addUseCase(capture)
        if (aspect == AspectChoice.THREE_TWO || aspect == AspectChoice.ONE_ONE) {
            val rotation = ContextCompat.getDisplayOrDefault(context).rotation
            val sideways = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
            // ViewPort's shape is width:height as the screen shows it.
            val shape = if (sideways) Rational(aspect.long, aspect.short) else Rational(aspect.short, aspect.long)
            useCases.setViewPort(ViewPort.Builder(shape, rotation).build())
        }

        isoChoice = null
        shutterChoiceNs = null
        meter.tracking = true
        meter.latest = null
        dialVisible = false
        cameraProvider.unbindAll()
        val bound = cameraProvider.bindToLifecycle(lifecycleOwner, selector, useCases.build())
        imageCapture = capture
        Camera2CameraControl.from(bound.cameraControl).clearCaptureRequestOptions()
        bound.cameraControl.setZoomRatio(1f)
        zoomRatio = 1f
        manualSupport = manualExposureSupport(Camera2CameraInfo.from(bound.cameraInfo))
        camera = bound
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        isCapturing = true
        view.performHapticFeedback(SHUTTER_HAPTIC)
        coroutineScope.launch {
            shutterBlink.snapTo(0.7f)
            shutterBlink.animateTo(0f, tween(220))
        }
        // Snapshotted now: zoom and any manual brightness offset, written into the photo's EXIF
        // so Review (and the Export) can show what the shot was taken with.
        val zoomAtCapture = zoomRatio
        val biasAtCapture = manualSupport?.let { support ->
            currentExposure(support)?.let { exposureOffsetStops(it, meter.latest ?: FALLBACK_METERED) }
        }
        val outputFile = captureStore.fileForNewCapture()
        // Selfies are saved as the mirrored preview showed them, like Google Camera's
        // default "mirror selfies".
        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile)
            .setMetadata(metadata)
            .build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) { captureStore.recordShootingSettings(zoomAtCapture, biasAtCapture) }
                        isCapturing = false
                        onCaptured()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    Log.e(TAG, "Capture failed", exception)
                }
            },
        )
    }

    /** Shutter tap: fire now, or start the self-timer; a tap during the countdown cancels it. */
    fun onShutter() {
        if (countdown != null) {
            countdownJob?.cancel()
            countdown = null
            return
        }
        if (timer == TimerChoice.OFF) {
            takePhoto()
            return
        }
        countdownJob = coroutineScope.launch {
            for (left in timer.seconds downTo 1) {
                countdown = left
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                delay(1000)
            }
            countdown = null
            takePhoto()
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })
        FrameOverlay(aspect = aspect, grid = gridOn, horizonAngle = if (levelOn) horizon else null)
        countdown?.let { Countdown(it) }
        ShutterBlink(shutterBlink.value)

        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            TopBar(
                gridOn = gridOn,
                onGridToggle = {
                    gridOn = !gridOn
                    settings.gridOn = gridOn
                },
                levelOn = levelOn,
                onLevelToggle = {
                    levelOn = !levelOn
                    settings.levelOn = levelOn
                },
                timer = timer,
                onTimerNext = {
                    timer = timer.next()
                    settings.timer = timer
                },
                aspect = aspect,
                onAspectNext = {
                    if (countdown == null && !isCapturing) {
                        aspect = aspect.next()
                        settings.aspect = aspect
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )

            val support = manualSupport
            // null while fully on Auto; otherwise what the camera is actually told to use.
            val exposure = support?.let { currentExposure(it) }
            val brightness = exposure?.let {
                brightnessReadout(exposureOffsetStops(it, meter.latest ?: FALLBACK_METERED))
            }
            val isoText = isoChoice?.let(::isoReadout) ?: exposure?.let { isoReadout(it.iso) } ?: "ISO Auto"
            val shutterText = shutterChoiceNs?.let(::shutterReadout)
                ?: exposure?.let { shutterReadout(it.exposureTimeNs) }
                ?: "Shutter Auto"

            // The panel's backing fades in with it; the tab row's own backing fades the other way.
            val panelAlpha by animateFloatAsState(if (dialVisible) 1f else 0f, tween(180), label = "panel")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 128.dp)
                    // Capped so a sideways screen doesn't stretch the ruler edge to edge.
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .background(PanelBackground.copy(alpha = PanelBackground.alpha * panelAlpha), RoundedCornerShape(18.dp))
                    .padding(8.dp),
            ) {
                AnimatedVisibility(
                    visible = dialVisible,
                    enter = fadeIn(tween(180)) + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut(tween(150)) + shrinkVertically(shrinkTowards = Alignment.Bottom),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = when (activeDial) {
                                Dial.ZOOM -> zoomReadout(zoomRatio)
                                Dial.ISO -> isoText
                                Dial.SHUTTER -> shutterText
                            },
                            color = PanelAccent,
                            fontSize = 14.sp,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (activeDial) {
                                Dial.ZOOM -> {
                                    val zoomState = camera?.cameraInfo?.zoomState?.value
                                    val minZoom = zoomState?.minZoomRatio ?: 1f
                                    val maxZoom = zoomState?.maxZoomRatio ?: 1f
                                    if (maxZoom > minZoom) {
                                        Ruler(
                                            fraction = zoomToSliderFraction(zoomRatio, minZoom, maxZoom),
                                            onFraction = { applyZoom(sliderFractionToZoom(it, minZoom, maxZoom)) },
                                            tickCount = 31,
                                            majorEvery = 5,
                                            dimmed = false,
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else {
                                        Box(Modifier.weight(1f))
                                    }
                                    ResetButton("1x", active = zoomRatio == 1f, onClick = { applyZoom(1f.coerceIn(minZoom, maxZoom)) })
                                }
                                Dial.ISO -> if (support != null) {
                                    val stops = support.isoStops
                                    val current = isoChoice ?: exposure?.iso ?: (meter.latest ?: FALLBACK_METERED).iso
                                    val index = stops.indices.minBy { abs(stops[it] - current) }
                                    Ruler(
                                        fraction = index.toFloat() / (stops.size - 1).coerceAtLeast(1),
                                        onFraction = { f ->
                                            val picked = stops[(f * (stops.size - 1)).roundToInt()]
                                            if (picked != isoChoice) {
                                                isoChoice = picked
                                                applyExposure()
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            }
                                            keepDialOpen()
                                        },
                                        tickCount = stops.size,
                                        majorEvery = 3,
                                        dimmed = isoChoice == null,
                                        modifier = Modifier.weight(1f),
                                    )
                                    ResetButton("Auto", active = isoChoice == null, onClick = {
                                        isoChoice = null
                                        applyExposure()
                                        keepDialOpen()
                                    })
                                }
                                Dial.SHUTTER -> if (support != null) {
                                    val stops = support.shutterStops
                                    val current = shutterChoiceNs ?: exposure?.exposureTimeNs
                                        ?: (meter.latest ?: FALLBACK_METERED).exposureTimeNs
                                    val index = stops.indices.minBy { abs(ln(stops[it].toDouble() / current)) }
                                    Ruler(
                                        fraction = index.toFloat() / (stops.size - 1).coerceAtLeast(1),
                                        onFraction = { f ->
                                            val picked = stops[(f * (stops.size - 1)).roundToInt()]
                                            if (picked != shutterChoiceNs) {
                                                shutterChoiceNs = picked
                                                applyExposure()
                                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            }
                                            keepDialOpen()
                                        },
                                        tickCount = stops.size,
                                        majorEvery = 3,
                                        dimmed = shutterChoiceNs == null,
                                        modifier = Modifier.weight(1f),
                                    )
                                    ResetButton("Auto", active = shutterChoiceNs == null, onClick = {
                                        shutterChoiceNs = null
                                        applyExposure()
                                        keepDialOpen()
                                    })
                                }
                            }
                        }
                    }
                }

                // The tab row is always visible; it carries its own backing while the panel is folded.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(PanelBackground.copy(alpha = PanelBackground.alpha * (1f - panelAlpha)), RoundedCornerShape(18.dp))
                        .padding(4.dp),
                ) {
                    ControlTab(
                        text = zoomReadout(zoomRatio),
                        selected = dialVisible && activeDial == Dial.ZOOM,
                        cameraChosen = false,
                        onClick = { toggleDial(Dial.ZOOM) },
                    )
                    if (support != null) {
                        ControlTab(
                            text = isoText,
                            selected = dialVisible && activeDial == Dial.ISO,
                            cameraChosen = isoChoice == null && exposure != null,
                            onClick = { toggleDial(Dial.ISO) },
                        )
                        ControlTab(
                            text = shutterText,
                            selected = dialVisible && activeDial == Dial.SHUTTER,
                            cameraChosen = shutterChoiceNs == null && exposure != null,
                            onClick = { toggleDial(Dial.SHUTTER) },
                        )
                    }
                    if (brightness != null) {
                        Text(
                            brightness,
                            color = PanelAccent,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            }

            // Shutter: a white disc inside a faint ring.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 30.dp)
                    .size(78.dp)
                    .border(3.dp, Paper, CircleShape)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(if (countdown != null) Amber else Paper)
                    .clickable(enabled = !isCapturing) { onShutter() },
            )

            if (hasFrontCamera) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 43.dp)
                        .offset(x = 108.dp)
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(PanelBackground)
                        .clickable(enabled = !isCapturing && countdown == null) {
                            onLensFacingChange(
                                if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.LENS_FACING_BACK
                                else CameraSelector.LENS_FACING_FRONT
                            )
                        },
                ) {
                    SwitchCameraIcon()
                }
            }
        }
    }
}

@OptIn(ExperimentalCamera2Interop::class)
private fun manualExposureSupport(info: Camera2CameraInfo): ManualExposureSupport? {
    val capabilities = info.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
    val isoRange = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
    val timeRange = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
    Log.i(
        TAG,
        "Camera ${info.cameraId}: manualSensor=" +
            "${capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)} " +
            "iso=$isoRange exposureNs=$timeRange",
    )
    if (capabilities == null || isoRange == null || timeRange == null) return null
    if (CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR !in capabilities) return null
    val shutters = shutterStops(timeRange.lower, timeRange.upper)
    if (shutters.isEmpty()) return null
    return ManualExposureSupport(
        isoRange = isoRange.lower..isoRange.upper,
        exposureTimeRangeNs = timeRange.lower..timeRange.upper.coerceAtMost(MAX_MANUAL_EXPOSURE_NS),
        isoStops = isoStops(isoRange.lower, isoRange.upper),
        shutterStops = shutters,
    )
}

private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ continuation.resume(future.get()) }, ContextCompat.getMainExecutor(context))
    }

private fun ProcessCameraProvider.hasCameraSafely(selector: CameraSelector): Boolean =
    try {
        hasCamera(selector)
    } catch (e: Exception) {
        false
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
