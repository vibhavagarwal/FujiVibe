package com.fujivibe.ui.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fujivibe.camera.CameraConnection
import com.fujivibe.camera.CameraConnector
import com.fujivibe.camera.LoadReport
import com.fujivibe.camera.PtpException
import com.fujivibe.camera.SlotDump
import com.fujivibe.camera.SlotLoader
import com.fujivibe.camera.SlotPlanStore
import com.fujivibe.recipe.Recipe
import com.fujivibe.recipe.RecipeLibrary
import com.fujivibe.recipe.RecipeTextParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * The state of the Camera recipes screen: the connection to the X-T5, what its seven slots hold, the
 * recipe the user has chosen for each of C2 to C7, and the result of the last load. C1 is the user's
 * default and is never part of the plan.
 */
class CameraRecipesModel(
    private val connector: CameraConnector,
    private val library: RecipeLibrary,
    private val store: SlotPlanStore,
    private val backupDir: File,
    private val scope: CoroutineScope,
) {
    var message by mutableStateOf(NOT_CONNECTED)
        private set
    var busy by mutableStateOf(false)
        private set
    var connected by mutableStateOf(false)
        private set
    var slots by mutableStateOf<List<SlotDump>>(emptyList())
        private set
    var recipes by mutableStateOf(library.all())
        private set
    var report by mutableStateOf<LoadReport?>(null)
        private set
    var hasBackup by mutableStateOf(SlotLoader(NoCamera, backupDir).latestBackup() != null)
        private set

    /** Slot number (2 to 7) to the id of the recipe chosen for it; a slot not in here is left as it is. */
    val plan = mutableStateMapOf<Int, String>().apply { putAll(store.plan) }

    private var connection: CameraConnection? = null

    fun recipeFor(slot: Int): Recipe? = plan[slot]?.let { id -> recipes.firstOrNull { it.id == id } }

    fun choose(slot: Int, recipe: Recipe?) {
        require(slot in 2..7) { "C1 is the default and is not changed" }
        if (recipe == null) plan.remove(slot) else plan[slot] = recipe.id
        store.plan = plan.toMap()
    }

    fun connect() {
        if (busy) return
        busy = true
        report = null
        message = "Looking for the camera…"
        when (val found = connector.find()) {
            CameraConnector.Found.NoCamera -> finish(NO_CAMERA)
            is CameraConnector.Found.NeedsPermission -> connector.requestPermission(found.device) { granted ->
                if (granted) open(found.device) else finish("USB permission was refused. Tap Connect again and choose Allow.")
            }
            is CameraConnector.Found.Ready -> open(found.device)
        }
    }

    private fun open(device: android.hardware.usb.UsbDevice) {
        scope.launch(Dispatchers.IO) {
            try {
                connection?.close()
                val c = connector.open(device)
                connection = c
                slots = c.session.readSlots()
                connected = true
                message = "Connected to ${c.info.manufacturer} ${c.info.model}, firmware ${c.info.deviceVersion}."
            } catch (e: Exception) {
                connected = false
                connection = null
                message = problem(e)
            }
            busy = false
        }
    }

    private fun finish(text: String) {
        message = text
        busy = false
    }

    /** What would be loaded: the chosen recipes by slot, ones no longer in the library dropped. */
    fun planned(): Map<Int, Recipe> = plan.mapNotNull { (slot, id) -> recipes.firstOrNull { it.id == id }?.let { slot to it } }.toMap()

    fun load() {
        val c = connection ?: return
        if (busy) return
        val toLoad = planned()
        busy = true
        message = "Loading ${toLoad.size} slot${if (toLoad.size == 1) "" else "s"}…"
        scope.launch(Dispatchers.IO) {
            try {
                val result = SlotLoader(c.session, backupDir).load(toLoad)
                report = result
                hasBackup = true
                slots = c.session.readSlots()
                message = if (result.allLoaded) "Done. Everything read back as written." else "Finished, with problems: see below."
            } catch (e: Exception) {
                message = problem(e)
            }
            busy = false
        }
    }

    fun restoreLast() {
        val c = connection ?: return
        if (busy) return
        val loader = SlotLoader(c.session, backupDir)
        val file = loader.latestBackup() ?: return
        busy = true
        message = "Restoring C2 to C7 from ${file.name}…"
        scope.launch(Dispatchers.IO) {
            try {
                val result = loader.restore(file)
                report = result
                slots = c.session.readSlots()
                message = if (result.allLoaded) "Restored from ${file.name}." else "Restored, with problems: see below."
            } catch (e: Exception) {
                message = problem(e)
            }
            busy = false
        }
    }

    /** Adds a recipe from pasted text; returns what the parser made of it, and chooses nothing. */
    fun addRecipe(text: String, name: String?): RecipeTextParser.Result {
        val result = library.addFromText(text, name)
        recipes = library.all()
        return result
    }

    fun disconnect() {
        val c = connection
        connection = null
        connected = false
        if (c != null) scope.launch(Dispatchers.IO) { runCatching { c.close() } }
    }

    private fun problem(e: Exception): String = when (e) {
        is PtpException -> "The camera did not answer as expected (${e.message}). Check it is awake with its screen lit, in USB RAW CONV./BACKUP RESTORE mode, then tap Connect again."
        else -> "Something went wrong: ${e.message ?: e::class.simpleName}"
    }

    companion object {
        const val NOT_CONNECTED = "Not connected. Put the camera in USB RAW CONV./BACKUP RESTORE mode, plug it in, and tap Connect."
        const val NO_CAMERA = "No camera found. Check that: the camera is on, awake, with its screen lit and well charged; its USB mode is RAW CONV./BACKUP RESTORE; the cable is in the phone; and in Settings, Connected devices, USB, \"USB controlled by\" is \"This device\"."
    }
}

/** A camera that is not there, so a loader can look for backup files without one. */
private object NoCamera : com.fujivibe.camera.SlotCamera {
    override fun readSlots(): List<SlotDump> = emptyList()
    override fun writeSlot(number: Int, values: Map<Int, ByteArray>, current: Map<Int, ByteArray>) =
        throw UnsupportedOperationException("No camera")
}
