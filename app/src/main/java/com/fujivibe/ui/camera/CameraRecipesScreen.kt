package com.fujivibe.ui.camera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fujivibe.R
import com.fujivibe.camera.LoadEntry
import com.fujivibe.camera.LoadReport
import com.fujivibe.camera.SlotDump
import com.fujivibe.camera.SlotNotes
import com.fujivibe.camera.SlotOutcome
import com.fujivibe.camera.SlotStatus
import com.fujivibe.camera.replacedName
import com.fujivibe.recipe.Recipe
import com.fujivibe.recipe.characterChips
import com.fujivibe.recipe.diffusionFilter
import com.fujivibe.recipe.slotName
import com.fujivibe.recipe.toSlotValues
import com.fujivibe.ui.HeaderIcon
import com.fujivibe.ui.ScreenHeader
import com.fujivibe.ui.theme.Amber
import com.fujivibe.ui.theme.Ink
import com.fujivibe.ui.theme.OnAmber
import com.fujivibe.ui.theme.Paper
import com.fujivibe.ui.theme.PaperMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardColor = Paper.copy(alpha = 0.09f)
internal val SheetColor = Color(0xFF1E1E1E)

/**
 * Camera recipes: choose a Recipe for each of the X-T5's custom slots C2 to C7 (C1 is the default
 * and stays as it is), then load them over USB. The camera applies the recipe as you shoot.
 */
@Composable
fun CameraRecipesScreen(model: CameraRecipesModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf<Int?>(null) }
    val slot = picking
    if (slot != null) {
        RecipePickerScreen(
            slot = slot,
            recipes = model.recipes,
            chosen = model.recipeFor(slot),
            otherSlotOf = { r -> model.plan.entries.firstOrNull { it.value == r.id && it.key != slot }?.key },
            onPick = { model.choose(slot, it); picking = null },
            onBack = { picking = null },
            modifier = modifier,
        )
    } else {
        RecipesMain(model, onBack, onPick = { picking = it }, modifier)
    }
}

@Composable
private fun RecipesMain(model: CameraRecipesModel, onBack: () -> Unit, onPick: (Int) -> Unit, modifier: Modifier) {
    BackHandler(onBack = onBack)
    var confirmLoad by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenHeader("Camera recipes", onBack) {
            Box {
                HeaderIcon(R.drawable.ic_more_vert, "More") { menuOpen = true }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Restore last backup") },
                        enabled = model.connected && !model.busy && model.hasBackup,
                        onClick = { menuOpen = false; model.restoreLast() },
                    )
                    DropdownMenuItem(
                        text = { Text("Add a recipe from text") },
                        enabled = !model.busy,
                        onClick = { menuOpen = false; adding = true },
                    )
                    DropdownMenuItem(
                        text = { Text(if (showRaw) "Hide raw slot values" else "Show raw slot values") },
                        enabled = model.slots.isNotEmpty(),
                        onClick = { menuOpen = false; showRaw = !showRaw },
                    )
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { ConnectionCard(model) }
            if (model.activity.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Amber, strokeWidth = 2.dp)
                        Text(model.activity, color = Paper, fontSize = 14.sp)
                    }
                }
            }
            item { SlotRow(1, model.slots.getOrNull(0), null, locked = true, onClick = {}) }
            items((2..7).toList()) { n ->
                SlotRow(n, model.slots.getOrNull(n - 1), model.recipeFor(n), locked = false, onClick = { if (!model.busy) onPick(n) })
            }
            model.report?.let { report -> item { ResultsCard(report, model.reportIsRestore) } }
            if (model.previousLoads.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = 10.dp)) {
                        Text("Previous loads", color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Tap one to use its recipes as the plan again. Nothing is sent to the camera until you load.", color = PaperMuted, fontSize = 13.sp)
                    }
                }
                items(model.previousLoads.take(5)) { entry -> PreviousLoadRow(entry, model.recipes) { model.usePreviousLoad(entry) } }
            }
            if (showRaw) items(model.slots) {
                Text(it.describe(), color = PaperMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
        }

        LoadBar(model, onLoad = { confirmLoad = true })
    }

    if (confirmLoad) ConfirmLoadSheet(model, onDismiss = { confirmLoad = false }, onLoad = { confirmLoad = false; model.load() })
    if (adding) AddRecipeDialog(model, onClose = { adding = false })
}

@Composable
private fun ConnectionCard(model: CameraRecipesModel) {
    if (model.connected) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardColor).padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = Amber, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Camera ready", color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(model.cameraLabel, color = PaperMuted, fontSize = 13.sp)
            }
            TextButton(onClick = model::connect, enabled = !model.busy) { Text("Reconnect", color = Amber) }
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardColor).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Connect the camera", color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        if (model.stage == ConnectionStage.NOT_TRIED) {
            Text(
                "Before you tap Connect: the camera is on with its screen lit and well charged, its USB mode is " +
                    "USB RAW CONV./BACKUP RESTORE, and the cable is in the phone. You can pick recipes now and connect later.",
                color = PaperMuted, fontSize = 13.sp,
            )
        }
        checklist(model.stage).forEachIndexed { index, item ->
            CheckRow(item, extra = if (index == 2 && item.state == CheckState.PROBLEM && model.problemDetail.isNotEmpty()) "The camera said: ${model.problemDetail}" else null)
        }
        Button(
            onClick = model::connect,
            enabled = !model.busy,
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber),
        ) { Text(if (model.stage == ConnectionStage.NOT_TRIED) "Connect" else "Try again") }
    }
}

@Composable
private fun CheckRow(item: CheckItem, extra: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when (item.state) {
                CheckState.OK -> Icon(painterResource(R.drawable.ic_check), contentDescription = "Done", tint = Amber, modifier = Modifier.size(22.dp))
                CheckState.PROBLEM -> Box(Modifier.size(20.dp).clip(CircleShape).background(Amber), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Problem", tint = OnAmber, modifier = Modifier.size(14.dp))
                }
                CheckState.WORKING -> CircularProgressIndicator(Modifier.size(18.dp), color = Amber, strokeWidth = 2.dp)
                CheckState.TODO -> Box(Modifier.size(14.dp).border(1.5.dp, Paper.copy(alpha = 0.35f), CircleShape))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                item.title,
                color = if (item.state == CheckState.TODO) PaperMuted else Paper,
                fontSize = 15.sp,
                fontWeight = if (item.state == CheckState.PROBLEM) FontWeight.Medium else FontWeight.Normal,
            )
            item.help?.let { Text(it, color = Paper, fontSize = 13.sp) }
            extra?.let { Text(it, color = PaperMuted, fontSize = 12.sp) }
        }
    }
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
        verticalAlignment = Alignment.Top,
    ) {
        Text("C$number", color = Amber, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.size(width = 44.dp, height = 24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                when {
                    locked -> "Your default, left as it is"
                    chosen != null -> chosen.name
                    else -> "Leave as it is"
                },
                color = if (chosen != null) Paper else PaperMuted,
                fontSize = 16.sp,
                fontWeight = if (chosen != null) FontWeight.Medium else FontWeight.Normal,
            )
            if (chosen != null) {
                Text((listOf(chosen.baseSimulation.label) + chosen.characterChips()).joinToString(" · "), color = PaperMuted, fontSize = 13.sp)
            }
            val cameraName = onCamera?.name
            when {
                onCamera == null -> Text("Not read from the camera yet", color = PaperMuted, fontSize = 12.sp)
                chosen != null && cameraName == chosen.slotName() -> Text("On the camera now", color = Amber, fontSize = 12.sp)
                chosen != null && !cameraName.isNullOrEmpty() -> Text("On the camera: $cameraName, will be replaced", color = PaperMuted, fontSize = 12.sp)
                cameraName.isNullOrEmpty() -> Text("On the camera: empty", color = PaperMuted, fontSize = 12.sp)
                else -> Text("On the camera: $cameraName", color = PaperMuted, fontSize = 12.sp)
            }
            if (chosen != null) {
                chosen.toSlotValues(onCamera ?: SlotDump(number, emptyMap())).notes.forEach {
                    Text(SlotNotes.plain(it), color = PaperMuted, fontSize = 12.sp)
                }
                chosen.diffusionFilter()?.let { Text("On the lens: $it", color = PaperMuted, fontSize = 12.sp) }
            }
        }
        if (!locked) Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Change", tint = PaperMuted, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun ResultsCard(report: LoadReport, isRestore: Boolean) {
    var details by remember { mutableStateOf(false) }
    val done = report.outcomes.count { it.status == SlotStatus.LOADED || it.status == SlotStatus.LOADED_WITH_NOTES }
    val total = report.outcomes.size
    val withNotes = report.outcomes.any { it.status == SlotStatus.LOADED_WITH_NOTES }
    val headline = when {
        isRestore && report.allLoaded -> "Backup put back"
        isRestore -> "Backup put back, with problems"
        report.allLoaded && !withNotes -> "All $total loaded, and read back from the camera"
        report.allLoaded -> "All $total loaded, with notes"
        else -> "Stopped: $done of $total loaded"
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardColor).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(headline, color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        report.outcomes.forEach { OutcomeRow(it) }
        TextButton(onClick = { details = !details }) { Text(if (details) "Hide details" else "Details", color = Amber) }
        if (details) Text("Backup saved first: ${report.backup.name}", color = PaperMuted, fontSize = 12.sp)
    }
}

@Composable
private fun OutcomeRow(o: SlotOutcome) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
            when (o.status) {
                SlotStatus.LOADED, SlotStatus.LOADED_WITH_NOTES ->
                    Icon(painterResource(R.drawable.ic_check), contentDescription = "Loaded", tint = Amber, modifier = Modifier.size(22.dp))
                SlotStatus.FAILED -> Box(Modifier.size(20.dp).clip(CircleShape).background(Amber), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Failed", tint = OnAmber, modifier = Modifier.size(14.dp))
                }
                SlotStatus.NOT_ATTEMPTED -> Box(Modifier.size(14.dp).border(1.5.dp, Paper.copy(alpha = 0.35f), CircleShape))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val word = when (o.status) {
                SlotStatus.LOADED -> "loaded and verified"
                SlotStatus.LOADED_WITH_NOTES -> "loaded, with notes"
                SlotStatus.FAILED -> "failed"
                SlotStatus.NOT_ATTEMPTED -> "not tried"
            }
            Text("C${o.slot}  ${o.recipeName}: $word", color = Paper, fontSize = 14.sp)
            if (o.detail.isNotEmpty()) Text(o.detail, color = PaperMuted, fontSize = 12.sp)
            o.notes.forEach { Text(SlotNotes.plain(it), color = PaperMuted, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun PreviousLoadRow(entry: LoadEntry, recipes: List<Recipe>, onUse: () -> Unit) {
    val whenText = remember(entry.at) { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(entry.at)) }
    val summary = entry.plan.toSortedMap().entries.joinToString("  ·  ") { (slot, id) ->
        "C$slot ${recipes.firstOrNull { it.id == id }?.name ?: "(removed)"}"
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardColor).clickable(onClick = onUse).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(whenText, color = Paper, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(summary, color = PaperMuted, fontSize = 12.sp)
        }
        Text("Use", color = Amber, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun LoadBar(model: CameraRecipesModel, onLoad: () -> Unit) {
    val count = model.planned().size
    val ready = model.connected && !model.busy && count > 0
    Column(Modifier.fillMaxWidth().background(Ink).padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(
            onClick = onLoad,
            enabled = ready,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber),
        ) { Text(if (count == 0) "Pick recipes to load" else "Load $count to camera", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        Text(
            when {
                model.busy -> "Working…"
                !model.connected -> "Connect the camera to load."
                count == 0 -> "Tap a slot to pick a recipe for it."
                else -> "All seven slots are backed up first. C1 is not touched."
            },
            color = PaperMuted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmLoadSheet(model: CameraRecipesModel, onDismiss: () -> Unit, onLoad: () -> Unit) {
    val planned = model.planned()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = SheetColor, contentColor = Paper) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Load ${planned.size} recipe${if (planned.size == 1) "" else "s"}?", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            planned.toSortedMap().forEach { (n, r) ->
                val replaced = model.slots.getOrNull(n - 1)?.let { replacedName(it, r) }.orEmpty()
                Column {
                    Text("C$n  ${r.name}", fontSize = 16.sp)
                    if (replaced.isNotEmpty()) Text("replaces $replaced", color = PaperMuted, fontSize = 13.sp)
                }
            }
            Text("All seven slots are backed up first, and C1 is not touched.", color = PaperMuted, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                Button(onClick = onLoad, colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber)) { Text("Load") }
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
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
