package com.fujivibe.ui.camera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fujivibe.R
import com.fujivibe.camera.SlotDump
import com.fujivibe.camera.SlotOutcome
import com.fujivibe.camera.SlotStatus
import com.fujivibe.camera.replacedName
import com.fujivibe.recipe.Recipe
import com.fujivibe.recipe.toSlotValues
import com.fujivibe.ui.theme.Amber
import com.fujivibe.ui.theme.OnAmber
import com.fujivibe.ui.theme.Paper
import com.fujivibe.ui.theme.PaperMuted

/**
 * Camera recipes: choose a Recipe for each of the X-T5's custom slots C2 to C7 (C1 is the default
 * and stays as it is), then load them over USB. The camera applies the recipe as you shoot.
 */
@Composable
fun CameraRecipesScreen(model: CameraRecipesModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)

    var picking by remember { mutableStateOf<Int?>(null) }
    var confirmLoad by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = Paper, modifier = Modifier.size(24.dp))
            }
            Text("Camera recipes", color = Paper, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Pick a recipe for each slot and load them into the X-T5. The camera then applies the recipe as you shoot. C1 stays as your default.",
                    color = PaperMuted, fontSize = 14.sp,
                )
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(model.message, color = Paper, fontSize = 14.sp)
                    Button(
                        onClick = model::connect,
                        enabled = !model.busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber),
                    ) { Text(if (model.connected) "Reconnect" else "Connect") }
                }
            }
            item { SlotRow(1, model.slots.getOrNull(0), null, locked = true, onClick = {}) }
            items((2..7).toList()) { n ->
                SlotRow(n, model.slots.getOrNull(n - 1), model.recipeFor(n), locked = false, onClick = { if (!model.busy) picking = n })
            }
            item { Summary(model) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { confirmLoad = true },
                        enabled = model.connected && !model.busy && model.planned().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber),
                    ) { Text("Load to camera") }
                    OutlinedButton(onClick = model::restoreLast, enabled = model.connected && !model.busy && model.hasBackup) { Text("Restore last backup") }
                }
            }
            item { OutlinedButton(onClick = { adding = true }, enabled = !model.busy) { Text("Add a recipe from text") } }
            model.report?.let { report ->
                item { Text("Backup saved first: ${report.backup.name}", color = PaperMuted, fontSize = 12.sp) }
                items(report.outcomes) { Outcome(it) }
            }
        }
    }

    picking?.let { slot ->
        RecipePicker(
            slot = slot,
            recipes = model.recipes,
            chosen = model.recipeFor(slot),
            onPick = { model.choose(slot, it); picking = null },
            onDismiss = { picking = null },
        )
    }
    if (confirmLoad) {
        val planned = model.planned()
        AlertDialog(
            onDismissRequest = { confirmLoad = false },
            title = { Text("Load ${planned.size} recipe${if (planned.size == 1) "" else "s"}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    planned.toSortedMap().forEach { (n, r) ->
                        val replaced = model.slots.getOrNull(n - 1)?.let { replacedName(it, r) }.orEmpty()
                        Text("C$n: ${r.name}" + if (replaced.isNotEmpty()) "  (replaces $replaced)" else "")
                    }
                    Text("All seven slots are backed up first, and C1 is not touched.", fontSize = 13.sp)
                }
            },
            confirmButton = { TextButton(onClick = { confirmLoad = false; model.load() }) { Text("Load") } },
            dismissButton = { TextButton(onClick = { confirmLoad = false }) { Text("Cancel") } },
        )
    }
    if (adding) AddRecipeDialog(model, onClose = { adding = false })
}

@Composable
private fun SlotRow(number: Int, onCamera: SlotDump?, chosen: Recipe?, locked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Paper.copy(alpha = if (locked) 0.04f else 0.09f))
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("C$number", color = Amber, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.size(width = 44.dp, height = 24.dp))
        Column(Modifier.weight(1f)) {
            val name = onCamera?.name
            Text(
                when {
                    onCamera == null -> "On camera: not read yet"
                    name.isNullOrEmpty() -> "On camera: empty"
                    else -> "On camera: $name"
                },
                color = PaperMuted, fontSize = 13.sp,
            )
            Text(
                when {
                    locked -> "Default, left as it is"
                    chosen != null -> "Load: ${chosen.name}"
                    else -> "Leave as it is"
                },
                color = if (chosen != null) Paper else PaperMuted,
                fontSize = 16.sp,
                fontWeight = if (chosen != null) FontWeight.Medium else FontWeight.Normal,
            )
        }
        if (!locked) Text("Change", color = Amber, fontSize = 13.sp)
    }
}

@Composable
private fun Summary(model: CameraRecipesModel) {
    val planned = model.planned().toSortedMap()
    if (planned.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        planned.forEach { (n, r) ->
            val current = model.slots.getOrNull(n - 1) ?: SlotDump(n, emptyMap())
            val notes = r.toSlotValues(current).notes
            val replaced = replacedName(current, r)
            if (replaced.isNotEmpty()) Text("C$n will replace $replaced.", color = Amber, fontSize = 13.sp)
            notes.forEach { Text("C$n, ${r.name}: $it.", color = PaperMuted, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun Outcome(o: SlotOutcome) {
    Column(Modifier.fillMaxWidth()) {
        val head = when (o.status) {
            SlotStatus.LOADED -> "C${o.slot}  ${o.recipeName}: loaded and read back"
            SlotStatus.LOADED_WITH_NOTES -> "C${o.slot}  ${o.recipeName}: loaded, with notes"
            SlotStatus.FAILED -> "C${o.slot}  ${o.recipeName}: failed"
            SlotStatus.NOT_ATTEMPTED -> "C${o.slot}  ${o.recipeName}: not attempted"
        }
        Text(head, color = if (o.status == SlotStatus.FAILED) Amber else Paper, fontSize = 14.sp)
        if (o.detail.isNotEmpty()) Text(o.detail, color = PaperMuted, fontSize = 12.sp)
        o.notes.forEach { Text("• $it", color = PaperMuted, fontSize = 12.sp) }
    }
}

@Composable
private fun RecipePicker(slot: Int, recipes: List<Recipe>, chosen: Recipe?, onPick: (Recipe?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recipe for C$slot") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PickerItem("Leave as it is", "Keep whatever the camera has in C$slot.", chosen == null) { onPick(null) }
                recipes.forEach { r -> PickerItem(r.name, describe(r), chosen?.id == r.id) { onPick(r) } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The user's own line if there is one, else the start of the recommendation, then the tags. */
private fun describe(r: Recipe): String {
    val line = r.userDescription?.takeIf { it.isNotBlank() } ?: r.recommendationText.substringBefore(". ").take(120)
    val tags = (r.bestFor.subjects.take(3) + r.bestFor.mood.take(2)).joinToString(", ")
    return listOf(line, tags).filter { it.isNotEmpty() }.joinToString("\n")
}

@Composable
private fun PickerItem(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Amber.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(title, fontWeight = FontWeight.Medium)
        if (detail.isNotEmpty()) Text(detail, fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.Gray)
    }
}

@Composable
private fun AddRecipeDialog(model: CameraRecipesModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var problems by remember { mutableStateOf<List<String>>(emptyList()) }
    var added by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add a recipe from text") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste the settings as a list, like \"Film Simulation: Classic Chrome\" on one line, \"Grain Effect: Weak, Small\" on the next.", fontSize = 13.sp)
                OutlinedTextField(name, { name = it; added = null }, label = { Text("Name (optional)") }, singleLine = true)
                OutlinedTextField(text, { text = it; added = null }, label = { Text("Settings") }, minLines = 6)
                added?.let { Text(it, fontSize = 13.sp) }
                problems.forEach { Text("• $it", fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(enabled = added == null && text.isNotBlank(), onClick = {
                val result = model.addRecipe(text, name)
                problems = result.warnings
                added = result.recipe?.let { "Added \"${it.name}\". Pick it for a slot from the list." }
                if (result.recipe != null && result.warnings.isEmpty()) onClose()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}
