package com.fujivibe.camera

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fujivibe.recipe.Recipe
import com.fujivibe.recipe.RecipeSettings
import com.fujivibe.recipe.RecipeJson
import com.fujivibe.recipe.RecipeTextParser
import com.fujivibe.recipe.applyTo
import com.fujivibe.recipe.dynamicRangeLimit
import com.fujivibe.recipe.slotName
import com.fujivibe.recipe.toSlotValues
import com.fujivibe.recipe.unsupportedSettings
import com.fujivibe.ui.theme.FujiVibeTheme
import com.fujivibe.ui.theme.Ink
import com.fujivibe.ui.theme.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Throwaway test bench for developing a RAF on a Fuji camera over USB (see .scratch/raw-develop).
 * Connect, pick a RAF, tap a film simulation; every step and its time goes in the log. It has its
 * own launcher icon so it stays out of the real app until the approach is proven.
 */
class UsbSpikeActivity : ComponentActivity() {

    private var onPermission: ((UsbDevice, Boolean) -> Unit)? = null

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // The typed overload needs Android 13; this app supports 10.
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) ?: return
            onPermission?.invoke(device, intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(
            this,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent { FujiVibeTheme { SpikeScreen() } }
    }

    override fun onDestroy() {
        unregisterReceiver(permissionReceiver)
        super.onDestroy()
    }

    private fun requestPermission(usb: UsbManager, device: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
        // Mutable because the system fills in the device and the answer; explicit, as Android requires.
        val pending = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        usb.requestPermission(device, pending)
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun SpikeScreen() {
        val usb = remember { getSystemService(UsbManager::class.java) }
        val scope = rememberCoroutineScope()
        val log = remember { mutableStateListOf<String>() }
        var busy by remember { mutableStateOf(false) }
        var link by remember { mutableStateOf<UsbPtpLink?>(null) }
        var session by remember { mutableStateOf<FujiRawSession?>(null) }
        var rafName by remember { mutableStateOf<String?>(null) }
        var raf by remember { mutableStateOf<ByteArray?>(null) }
        var uploaded by remember { mutableStateOf(false) }
        var result by remember { mutableStateOf<Bitmap?>(null) }
        var fullSize by remember { mutableStateOf(true) }
        var deviceInfo by remember { mutableStateOf<DeviceInfo?>(null) }
        var skipWhiteBalance by remember { mutableStateOf(false) }
        var bisectMode by remember { mutableStateOf(false) }
        var targetSlot by remember { mutableStateOf<Int?>(null) }
        var pendingWrite by remember { mutableStateOf<Recipe?>(null) }
        val recipes = remember {
            try {
                RecipeJson.parse(assets.open("recipes.json").bufferedReader().use { it.readText() })
            } catch (e: Exception) {
                log.add("Could not load recipes: ${e.message}")
                emptyList()
            }
        }
        val started = remember { System.currentTimeMillis() }

        fun say(line: String) {
            log.add("%5.1fs  %s".format((System.currentTimeMillis() - started) / 1000.0, line))
        }

        /** Runs [block] off the main thread, logging how long it took and any failure. */
        fun work(what: String, block: () -> Unit) {
            if (busy) return
            busy = true
            scope.launch {
                val t0 = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    try {
                        block()
                        say("$what: done in ${System.currentTimeMillis() - t0} ms")
                    } catch (e: Throwable) {
                        say("$what FAILED after ${System.currentTimeMillis() - t0} ms: ${e::class.simpleName}: ${e.message}")
                    }
                }
                busy = false
            }
        }

        fun connect(device: UsbDevice) = work("Connect") {
            link?.close()
            val opened = UsbPtpLink.open(usb, device)
            say(opened.description)
            val s = FujiRawSession(opened.ptp, ::say)
            deviceInfo = s.open()
            link = opened
            session = s
            uploaded = false
        }

        onPermission = { device, granted ->
            if (granted) connect(device) else say("USB permission was refused")
        }

        val pickRaf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            work("Read RAF") {
                val name = contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
                } ?: uri.lastPathSegment ?: "RAF"
                val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                rafName = name
                raf = bytes
                uploaded = false
                say("$name: ${bytes.size / 1024 / 1024} MB")
            }
        }

        /** Renders with [edit] applied to the RAF's recipe, logs the size, saves the JPEG as [stem], and shows it. */
        fun renderAndSave(s: FujiRawSession, stem: String, edit: (RawConvProfile) -> RawConvProfile) {
            val t1 = System.currentTimeMillis()
            val jpeg = s.render { edit(it).let { p -> if (fullSize) p.withFullSize() else p }.also { sent -> say("Sending: ${sent.describe()}") } }
            say("Render + download: ${System.currentTimeMillis() - t1} ms, ${jpeg.size / 1024} KB")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
            say("Result is ${bounds.outWidth} x ${bounds.outHeight} px")
            val fileName = "${rafName ?: "raf"}-$stem.jpg"
            File(getExternalFilesDir(null), fileName).writeBytes(jpeg)
            // Also into Pictures, so the Photos app shows it without a trip to the PC.
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RAF Spike")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)!!.use { it.write(jpeg) }
                say("Saved to Pictures/RAF Spike/$fileName")
            } else {
                say("Could not save to Pictures")
            }
            result = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = 4 })
        }

        /** Uploads the RAF if the camera does not have it yet. */
        fun ensureUploaded(): FujiRawSession {
            val s = session ?: error("Connect first")
            val bytes = raf ?: error("Pick a RAF first")
            if (!uploaded) {
                val t0 = System.currentTimeMillis()
                s.upload(bytes)
                uploaded = true
                say("Upload: ${System.currentTimeMillis() - t0} ms")
            }
            return s
        }

        fun develop(sim: FujiFilmSim?, tweak: Tweak? = null, recipe: Recipe? = null) =
            work("Develop ${recipe?.name ?: tweak?.label ?: sim?.label ?: "as shot"}") {
                recipe?.unsupportedSettings()?.takeIf { it.isNotEmpty() }?.let { say("Not applied yet: ${it.joinToString()}") }
                val s = ensureUploaded()
                // Dropping the white balance isolates whether it is what tints a result.
                val used = if (recipe != null && skipWhiteBalance) recipe.copy(settings = recipe.settings.copy(whiteBalance = null)) else recipe
                recipe?.dynamicRangeLimit(s.baseProfile!!)?.let { say("Note: $it") }
                renderAndSave(s, recipe?.id ?: tweak?.name ?: sim?.name ?: "AS_SHOT") {
                    val withSim = if (sim == null) it else it.withFilmSimulation(sim.code)
                    val withTweak = if (tweak == null) withSim else tweak.edit(withSim)
                    if (used == null) withTweak else used.applyTo(it)
                }
            }

        /**
         * Renders [recipe] whole, then once per setting group with just that group left out, so
         * whichever omission changes a result the most shows what is behind it.
         */
        fun bisect(recipe: Recipe) = work("Bisect ${recipe.name}") {
            val s = ensureUploaded()
            val st = recipe.settings
            val groups = listOf<Pair<String, RecipeSettings>>(
                "no-dr" to st.copy(dynamicRange = null, dRangePriority = null),
                "no-grain" to st.copy(grain = null),
                "no-chrome" to st.copy(colorChrome = null),
                "no-chromeblue" to st.copy(colorChromeFxBlue = null),
                "no-wb" to st.copy(whiteBalance = null),
                "no-highlight" to st.copy(highlight = null),
                "no-shadow" to st.copy(shadow = null),
                "no-color" to st.copy(color = null),
                "no-sharpness" to st.copy(sharpness = null),
                "no-clarity" to st.copy(clarity = null),
            ).filter { it.second != st } // a group the recipe does not set changes nothing
            renderAndSave(s, "${recipe.id}-all") { recipe.applyTo(it) }
            for ((name, without) in groups) {
                say("Leaving out: $name")
                renderAndSave(s, "${recipe.id}-$name") { recipe.copy(settings = without).applyTo(it) }
            }
        }

        /** Renders once per image-size code and logs the pixel size each gave, to learn what the codes mean. Saves nothing. */
        fun probeSizes() = work("Probe sizes") {
            val s = session ?: error("Connect first")
            val bytes = raf ?: error("Pick a RAF first")
            if (!uploaded) {
                s.upload(bytes)
                uploaded = true
            }
            val asShot = s.baseProfile!!
            say("As shot: size=${asShot.field(RawConvProfile.IMAGE_SIZE)} quality=${asShot.field(RawConvProfile.IMAGE_QUALITY)}")
            for (code in 0..12) {
                try {
                    val jpeg = s.render { it.with(RawConvProfile.IMAGE_SIZE, code) }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
                    say("size code $code -> ${bounds.outWidth} x ${bounds.outHeight} px, ${jpeg.size / 1024} KB")
                } catch (e: Throwable) {
                    say("size code $code failed: ${e.message}")
                }
            }
        }

        /** Renders once per value of recipe parameter [index] and logs the pixel size each gave. Saves nothing. */
        fun probeField(index: Int, values: IntRange) = work("Probe parameter $index") {
            val s = session ?: error("Connect first")
            val bytes = raf ?: error("Pick a RAF first")
            if (!uploaded) {
                s.upload(bytes)
                uploaded = true
            }
            say("Parameter $index as shot: ${s.baseProfile!!.field(index)}")
            for (value in values) {
                try {
                    val jpeg = s.render { it.with(index, value) }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
                    say("p$index=$value -> ${bounds.outWidth} x ${bounds.outHeight} px, ${jpeg.size / 1024} KB")
                } catch (e: Throwable) {
                    say("p$index=$value failed: ${e.message}")
                }
            }
        }

        /** Saves every slot's raw values to a file the app owns, so a write can be undone. */
        fun backUpSlots(slots: List<SlotDump>): File {
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
            val file = File(getExternalFilesDir(null), "slots-backup-$stamp.txt")
            file.writeText(slots.joinToString("\n") { it.backupLine() } + "\n")
            return file
        }

        /** Reads the seven custom slots, logs them, and saves a raw backup. Writes nothing but the slot selector. */
        fun readSlots() = work("Read slots") {
            val s = session ?: error("Connect first")
            val slots = s.readSlots()
            slots.forEach { say(it.describe()) }
            say("Backup saved: ${backUpSlots(slots).absolutePath}")
        }

        /**
         * Backs up all slots, then writes [recipe] into slot [number] and reads it back. Refuses a
         * slot that has a name, so this test cannot overwrite a recipe the user set up; a slot that
         * already carries this recipe's own name (an earlier write) may be written again.
         */
        fun writeRecipeToSlot(recipe: Recipe, number: Int) = work("Write ${recipe.name} to C$number") {
            val s = session ?: error("Connect first")
            val slots = s.readSlots()
            say("Backup saved: ${backUpSlots(slots).absolutePath}")
            val target = slots[number - 1]
            if (target.name.isNotEmpty() && target.name != recipe.slotName()) {
                say("C$number holds \"${target.name}\"; this test writes only to unnamed slots. Nothing written.")
                return@work
            }
            val write = recipe.toSlotValues(target)
            write.notes.forEach { say("Note: $it") }
            val result = s.writeSlot(number, write.values, target.props)
            say("Wrote ${result.written} settings into C$number")
            result.rejected.forEach { say("Camera refused: $it") }
            result.mismatches.forEach { say("Mismatch: $it") }
            if (result.ok) say("All of them read back as written")
            say(s.readSlots()[number - 1].describe())
        }

        /** Puts slot [number] back as it was in the oldest backup the app holds (the state before any write). */
        fun restoreSlot(number: Int) = work("Restore C$number") {
            val s = session ?: error("Connect first")
            val file = getExternalFilesDir(null)?.listFiles { f -> f.name.startsWith("slots-backup-") }
                ?.minByOrNull { it.name } ?: error("No backup file found")
            val saved = file.readLines().mapNotNull { SlotDump.fromBackupLine(it) }.firstOrNull { it.number == number }
                ?: error("${file.name} has no C$number")
            say("Restoring C$number from ${file.name}: \"${saved.name}\"")
            val result = s.writeSlot(number, saved.props, s.readSlots()[number - 1].props)
            say("Wrote ${result.written} settings into C$number")
            result.rejected.forEach { say("Camera refused: $it") }
            result.mismatches.forEach { say("Mismatch: $it") }
            say(s.readSlots()[number - 1].describe())
        }

        /** Every property the camera lists, with the first bytes of its value. */
        fun dumpProps() = work("Dump properties") {
            val s = session ?: error("Connect first")
            val props = deviceInfo?.properties?.sorted() ?: error("Connect first")
            say("${props.size} properties")
            for (code in props) {
                try {
                    val data = s.readProperty(code)
                    val head = data.take(16).joinToString("") { "%02x".format(it) }
                    say("${hex(code)} [${data.size}] $head")
                } catch (e: Throwable) {
                    say("${hex(code)} failed: ${e.message}")
                }
            }
        }

        Column(
            Modifier.fillMaxSize().background(Ink).statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("RAF Spike", color = Paper, fontSize = 20.sp)
            Text(
                "1. Camera in USB RAW CONV. mode, cable in.  2. Connect.  3. Pick a RAF from the phone.  4. Tap a look.",
                color = Paper.copy(alpha = 0.7f),
                fontSize = 13.sp,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    val device = UsbPtpLink.findFuji(usb)
                    // What Android lists, so a missing camera can be told from one under another ID.
                    val everything = usb.deviceList.values
                    say(
                        if (everything.isEmpty()) "USB devices seen: none"
                        else "USB devices seen: " + everything.joinToString("; ") {
                            "${it.productName ?: it.deviceName} vid=${hex(it.vendorId)} pid=${hex(it.productId)}"
                        },
                    )
                    when {
                        device == null -> say("No Fuji camera on USB (looked for vendor ${hex(FUJI_VENDOR_ID)})")
                        usb.hasPermission(device) -> connect(device)
                        else -> requestPermission(usb, device)
                    }
                }) { Text(if (session == null) "Connect" else "Reconnect") }
                OutlinedButton(enabled = !busy, onClick = { pickRaf.launch(arrayOf("*/*")) }) {
                    Text(rafName ?: "Pick RAF")
                }
                OutlinedButton(onClick = { fullSize = !fullSize }) {
                    Text(if (fullSize) "Full size: ON" else "Full size: off")
                }
                OutlinedButton(onClick = { skipWhiteBalance = !skipWhiteBalance }) {
                    Text(if (skipWhiteBalance) "Skip WB: ON" else "Skip WB: off")
                }
                OutlinedButton(onClick = { targetSlot = when (val t = targetSlot) { null -> 5; 7 -> null; else -> t + 1 } }) {
                    Text(targetSlot?.let { "Write to: C$it" } ?: "Write to: none")
                }
                OutlinedButton(enabled = !busy && targetSlot != null, onClick = { restoreSlot(targetSlot!!) }) {
                    Text(targetSlot?.let { "Restore C$it" } ?: "Restore slot")
                }
                OutlinedButton(onClick = { bisectMode = !bisectMode }) {
                    Text(if (bisectMode) "Bisect: ON" else "Bisect: off")
                }
                OutlinedButton(enabled = !busy, onClick = { probeSizes() }) { Text("Probe sizes") }
                OutlinedButton(enabled = !busy, onClick = { probeField(0, 0..6) }) { Text("Probe p0") }
                OutlinedButton(enabled = !busy, onClick = { probeField(4, 0..6) }) { Text("Probe p4") }
                OutlinedButton(enabled = !busy, onClick = { dumpProps() }) { Text("Dump props") }
                OutlinedButton(enabled = !busy, onClick = { readSlots() }) { Text("Read slots") }
                OutlinedButton(onClick = {
                    val clipboard = getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("RAF Spike log", log.joinToString("\n")))
                    say("Log copied (${log.size} lines)")
                }) { Text("Copy log") }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = { develop(null) }) { Text("As shot") }
                FujiFilmSim.entries.forEach { sim ->
                    OutlinedButton(enabled = !busy, onClick = { develop(sim) }) { Text(sim.label) }
                }
            }
            pendingWrite?.let { recipe ->
                val slot = targetSlot ?: 0
                AlertDialog(
                    onDismissRequest = { pendingWrite = null },
                    title = { Text("Write ${recipe.name} to C$slot?") },
                    text = { Text("The camera's slot C$slot is set to \"${recipe.slotName()}\". A backup of all seven slots is saved first, and a slot that already has a name is left alone.") },
                    confirmButton = { TextButton(onClick = { pendingWrite = null; writeRecipeToSlot(recipe, slot) }) { Text("Write") } },
                    dismissButton = { TextButton(onClick = { pendingWrite = null }) { Text("Cancel") } },
                )
            }
            Text("Recipes (${recipes.size}). Write to: picks a camera slot, then a recipe writes there:", color = Paper.copy(alpha = 0.7f), fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = {
                    val clipboard = getSystemService(ClipboardManager::class.java)
                    val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this@UsbSpikeActivity)?.toString().orEmpty()
                    val parsed = RecipeTextParser.parse(text)
                    parsed.warnings.forEach { say("Paste: $it") }
                    val pasted = parsed.recipe
                    if (pasted == null) say("Paste: nothing to develop with. Copy a recipe's settings first.")
                    else {
                        say("Pasted recipe \"${pasted.name}\": ${pasted.baseSimulation.label}, ${pasted.settings}")
                        develop(null, recipe = pasted)
                    }
                }) { Text("Paste recipe") }
                recipes.forEach { recipe ->
                    OutlinedButton(enabled = !busy, onClick = {
                        when {
                            targetSlot != null -> pendingWrite = recipe
                            bisectMode -> bisect(recipe)
                            else -> develop(null, recipe = recipe)
                        }
                    }) { Text(recipe.name) }
                }
            }
            Text("Recipe fields (on the RAF's own film simulation):", color = Paper.copy(alpha = 0.7f), fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TWEAKS.forEach { tweak ->
                    OutlinedButton(enabled = !busy, onClick = { develop(null, tweak) }) { Text(tweak.label) }
                }
            }
            if (busy) Text("Working…", color = Paper)
            result?.let {
                Image(
                    it.asImageBitmap(),
                    contentDescription = "Rendered by the camera",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                log.joinToString("\n"),
                color = Paper,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
    }

    /** One recipe field changed, to see whether the camera honours it. Encodings are FilmKit's. */
    private class Tweak(val name: String, val label: String, val edit: (RawConvProfile) -> RawConvProfile)

    private companion object {
        const val ACTION_USB_PERMISSION = "com.fujivibe.USB_PERMISSION"

        val TWEAKS = listOf(
            Tweak("GRAIN_STRONG_LARGE", "Grain strong large") { it.with(RawConvProfile.GRAIN, 5) },
            Tweak("CHROME_STRONG", "Color Chrome strong") { it.with(RawConvProfile.COLOR_CHROME, 3) },
            Tweak("EXPOSURE_PLUS_1", "Exposure +1 EV") { it.with(RawConvProfile.EXPOSURE_BIAS, 1000) },
            Tweak("HIGHLIGHT_MINUS_2", "Highlights -2") { it.with(RawConvProfile.HIGHLIGHT_TONE, -20) },
            Tweak("SHADOW_PLUS_2", "Shadows +2") { it.with(RawConvProfile.SHADOW_TONE, 20) },
        )
    }
}
