package com.fujivibe.ui.camera

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fujivibe.camera.CameraConnection
import com.fujivibe.camera.CameraConnector
import com.fujivibe.camera.LoadEntry
import com.fujivibe.camera.LoadHistoryStore
import com.fujivibe.camera.LoadReport
import com.fujivibe.camera.SlotDump
import com.fujivibe.camera.SlotLoader
import com.fujivibe.camera.SlotPlanStore
import com.fujivibe.camera.SuggestStore
import com.fujivibe.recipe.Recipe
import com.fujivibe.recipe.RecipeLibrary
import com.fujivibe.recipe.RecipeTextParser
import com.fujivibe.recipe.Recommender
import com.fujivibe.recipe.SuggestRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * The state of the Camera recipes screen: how far the connection to the X-T5 has got, what its seven
 * slots hold, the recipe the user has chosen for each of C2 to C7, the result of the last load, and the
 * loads that worked before. C1 is the user's default and is never part of the plan.
 */
class CameraRecipesModel(
    private val connector: CameraConnector,
    private val library: RecipeLibrary,
    private val store: SlotPlanStore,
    private val history: LoadHistoryStore,
    val suggestStore: SuggestStore,
    private val backupDir: File,
    private val scope: CoroutineScope,
) {
    var stage by mutableStateOf(ConnectionStage.NOT_TRIED)
        private set

    /** What the camera said when it did not answer, for the checklist to show under its help. */
    var problemDetail by mutableStateOf("")
        private set

    /** The camera's make and model and firmware, once connected. */
    var cameraLabel by mutableStateOf("")
        private set

    /** What is happening now ("Loading 3 slots…"), or empty. */
    var activity by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    val connected: Boolean get() = stage == ConnectionStage.CONNECTED
    var slots by mutableStateOf<List<SlotDump>>(emptyList())
        private set
    var recipes by mutableStateOf(library.all())
        private set
    var report by mutableStateOf<LoadReport?>(null)
        private set

    /** Whether [report] is from putting a backup back rather than from loading recipes. */
    var reportIsRestore by mutableStateOf(false)
        private set
    var hasBackup by mutableStateOf(SlotLoader(NoCamera, backupDir).latestBackup() != null)
        private set
    var previousLoads by mutableStateOf(history.entries)
        private set

    /** Slot number (2 to 7) to the id of the recipe chosen for it; a slot not in here is left as it is. */
    val plan = mutableStateMapOf<Int, String>().apply { putAll(store.plan) }

    /** Slots Suggest leaves alone. */
    var locked by mutableStateOf(suggestStore.locked)
        private set

    /** Why Suggest put each recipe in its slot; a slot the user then changes by hand loses its reason. */
    val reasons = mutableStateMapOf<Int, String>()

    /** Ticked tags nothing is tagged for, from the last Suggest. */
    var suggestionGaps by mutableStateOf<List<String>>(emptyList())
        private set

    private var connection: CameraConnection? = null

    fun recipeFor(slot: Int): Recipe? = plan[slot]?.let { id -> recipes.firstOrNull { it.id == id } }

    fun choose(slot: Int, recipe: Recipe?) {
        require(slot in 2..7) { "C1 is the default and is not changed" }
        if (recipe == null) plan.remove(slot) else plan[slot] = recipe.id
        reasons.remove(slot)
        store.plan = plan.toMap()
    }

    fun toggleLock(slot: Int) {
        locked = if (slot in locked) locked - slot else locked + slot
        suggestStore.locked = locked
    }

    /**
     * Fills the slots that are not locked with recipes for the ticked tags, as a draft the user can change.
     * Nothing goes to the camera until they load.
     */
    fun suggest(subjects: Set<String>, light: Set<String>, mood: Set<String>, includeMonochrome: Boolean, hasFilter: Boolean) {
        suggestStore.subjects = subjects
        suggestStore.light = light
        suggestStore.mood = mood
        suggestStore.includeMonochrome = includeMonochrome
        suggestStore.hasFilter = hasFilter
        val keep = locked.mapNotNull { slot -> recipeFor(slot)?.let { slot to it } }.toMap()
        val result = Recommender.suggest(
            recipes,
            SuggestRequest(subjects, light, mood, includeMonochrome, hasFilter, locked = keep),
        )
        (2..7).filter { it !in keep }.forEach { plan.remove(it); reasons.remove(it) }
        result.picks.forEach { plan[it.slot] = it.recipe.id; reasons[it.slot] = it.reason }
        store.plan = plan.toMap()
        suggestionGaps = result.gaps
    }

    /** Uses an earlier load's recipes as the plan. Recipes that are no longer in the library are left out. */
    fun usePreviousLoad(entry: LoadEntry) {
        plan.clear()
        reasons.clear()
        entry.plan.filterValues { id -> recipes.any { it.id == id } }.forEach { (slot, id) -> plan[slot] = id }
        store.plan = plan.toMap()
    }

    fun connect() {
        if (busy) return
        busy = true
        report = null
        problemDetail = ""
        stage = ConnectionStage.LOOKING
        when (val found = connector.find()) {
            CameraConnector.Found.NoCamera -> finish(ConnectionStage.NO_CAMERA)
            is CameraConnector.Found.NeedsPermission -> {
                stage = ConnectionStage.ASKING_PERMISSION
                connector.requestPermission(found.device) { granted ->
                    if (granted) open(found.device) else finish(ConnectionStage.PERMISSION_REFUSED)
                }
            }
            is CameraConnector.Found.Ready -> open(found.device)
        }
    }

    private fun open(device: android.hardware.usb.UsbDevice) {
        stage = ConnectionStage.OPENING
        scope.launch(Dispatchers.IO) {
            try {
                connection?.close()
                val c = connector.open(device)
                connection = c
                slots = c.session.readSlots()
                cameraLabel = "${c.info.model}, firmware ${c.info.deviceVersion}"
                stage = ConnectionStage.CONNECTED
            } catch (e: Exception) {
                connection = null
                problemDetail = e.message ?: e::class.simpleName.orEmpty()
                stage = ConnectionStage.NOT_ANSWERING
            }
            busy = false
        }
    }

    private fun finish(newStage: ConnectionStage) {
        stage = newStage
        busy = false
    }

    /** What would be loaded: the chosen recipes by slot, ones no longer in the library dropped. */
    fun planned(): Map<Int, Recipe> = plan.mapNotNull { (slot, id) -> recipes.firstOrNull { it.id == id }?.let { slot to it } }.toMap()

    fun load() {
        val c = connection ?: return
        if (busy) return
        val toLoad = planned()
        busy = true
        activity = "Loading ${toLoad.size} slot${if (toLoad.size == 1) "" else "s"}… keep the cable in and the camera awake."
        scope.launch(Dispatchers.IO) {
            try {
                val result = SlotLoader(c.session, backupDir).load(toLoad)
                report = result
                reportIsRestore = false
                hasBackup = true
                slots = c.session.readSlots()
                if (result.allLoaded) previousLoads = history.record(plan.toMap())
            } catch (e: Exception) {
                lostConnection(e)
            }
            activity = ""
            busy = false
        }
    }

    fun restoreLast() {
        val c = connection ?: return
        if (busy) return
        val loader = SlotLoader(c.session, backupDir)
        val file = loader.latestBackup() ?: return
        busy = true
        activity = "Putting C2 to C7 back from ${file.name}…"
        scope.launch(Dispatchers.IO) {
            try {
                val result = loader.restore(file)
                report = result
                reportIsRestore = true
                slots = c.session.readSlots()
            } catch (e: Exception) {
                lostConnection(e)
            }
            activity = ""
            busy = false
        }
    }

    /** The camera stopped answering part-way: back to the checklist so the user can see what to check. */
    private fun lostConnection(e: Exception) {
        connection = null
        problemDetail = e.message ?: e::class.simpleName.orEmpty()
        stage = ConnectionStage.NOT_ANSWERING
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
        stage = ConnectionStage.NOT_TRIED
        slots = emptyList()
        if (c != null) scope.launch(Dispatchers.IO) { runCatching { c.close() } }
    }
}

/** A camera that is not there, so a loader can look for backup files without one. */
private object NoCamera : com.fujivibe.camera.SlotCamera {
    override fun readSlots(): List<SlotDump> = emptyList()
    override fun writeSlot(number: Int, values: Map<Int, ByteArray>, current: Map<Int, ByteArray>) =
        throw UnsupportedOperationException("No camera")
}
