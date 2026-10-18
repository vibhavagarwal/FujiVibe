package com.fujivibe.camera

import com.fujivibe.recipe.Recipe
import com.fujivibe.recipe.slotName
import com.fujivibe.recipe.toSlotValues
import java.io.File

enum class SlotStatus { LOADED, LOADED_WITH_NOTES, FAILED, NOT_ATTEMPTED }

/** What happened to one slot. [notes] are settings that could not be written or were refused; [detail] says why a slot failed. */
class SlotOutcome(
    val slot: Int,
    val recipeName: String,
    val status: SlotStatus,
    val notes: List<String> = emptyList(),
    val detail: String = "",
)

class LoadReport(val backup: File, val outcomes: List<SlotOutcome>) {
    val allLoaded get() = outcomes.all { it.status == SlotStatus.LOADED || it.status == SlotStatus.LOADED_WITH_NOTES }
}

/**
 * Loads a plan (slot number to recipe) into the camera's custom slots. C1 is never written: it is
 * the user's default. Every slot is backed up to a file first, each write is read back by
 * [SlotCamera.writeSlot], and the first failure stops the rest.
 */
class SlotLoader(private val camera: SlotCamera, private val backupDir: File) {

    fun load(plan: Map<Int, Recipe>): LoadReport {
        require(plan.keys.all { it in 2..SLOT_COUNT }) { "Only C2 to C7 can be loaded; C1 is the default and is left alone" }
        val slots = camera.readSlots()
        val backup = backUp(slots)
        val outcomes = mutableListOf<SlotOutcome>()
        var stopped = false
        for ((number, recipe) in plan.toSortedMap()) {
            if (stopped) {
                outcomes += SlotOutcome(number, recipe.name, SlotStatus.NOT_ATTEMPTED, detail = "Stopped after an earlier slot failed")
                continue
            }
            val current = slots[number - 1]
            val write = recipe.toSlotValues(current)
            try {
                val result = camera.writeSlot(number, write.values, current.props)
                val notes = write.notes + result.rejected.map { "The camera refused $it" } + result.mismatches.map { "Did not read back: $it" }
                outcomes += SlotOutcome(
                    number, recipe.name,
                    if (notes.isEmpty()) SlotStatus.LOADED else SlotStatus.LOADED_WITH_NOTES,
                    notes,
                )
            } catch (e: Exception) {
                outcomes += SlotOutcome(number, recipe.name, SlotStatus.FAILED, detail = e.message ?: e::class.simpleName.orEmpty())
                stopped = true
            }
        }
        return LoadReport(backup, outcomes)
    }

    /** Puts C2 to C7 back as they were in [backup]. C1 is not touched. */
    fun restore(backup: File): LoadReport {
        val saved = backup.readLines().mapNotNull { SlotDump.fromBackupLine(it) }.associateBy { it.number }
        val slots = camera.readSlots()
        val outcomes = mutableListOf<SlotOutcome>()
        for (number in 2..SLOT_COUNT) {
            val old = saved[number] ?: continue
            try {
                val result = camera.writeSlot(number, old.props, slots[number - 1].props)
                val notes = result.rejected.map { "The camera refused $it" } + result.mismatches.map { "Did not read back: $it" }
                outcomes += SlotOutcome(number, old.name.ifEmpty { "(empty)" }, if (notes.isEmpty()) SlotStatus.LOADED else SlotStatus.LOADED_WITH_NOTES, notes)
            } catch (e: Exception) {
                outcomes += SlotOutcome(number, old.name.ifEmpty { "(empty)" }, SlotStatus.FAILED, detail = e.message.orEmpty())
                break
            }
        }
        return LoadReport(backup, outcomes)
    }

    /** The newest backup file, or null if there is none. */
    fun latestBackup(): File? = backupDir.listFiles { f -> f.name.startsWith(BACKUP_PREFIX) }?.maxByOrNull { it.name }

    private fun backUp(slots: List<SlotDump>): File {
        backupDir.mkdirs()
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        var file = File(backupDir, "$BACKUP_PREFIX$stamp.txt")
        var n = 1
        while (file.exists()) file = File(backupDir, "$BACKUP_PREFIX$stamp-${n++}.txt") // two loads in one second
        file.writeText(slots.joinToString("\n") { it.backupLine() } + "\n")
        return file
    }

    companion object {
        const val SLOT_COUNT = 7
        const val BACKUP_PREFIX = "slots-backup-"
    }
}

/** A slot's name for a recipe that is about to replace what the slot holds; empty when the slot is empty or already has it. */
fun replacedName(current: SlotDump, recipe: Recipe): String =
    current.name.takeIf { it.isNotEmpty() && it != recipe.slotName() }.orEmpty()
