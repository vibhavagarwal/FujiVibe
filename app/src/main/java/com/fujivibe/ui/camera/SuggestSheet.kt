package com.fujivibe.ui.camera

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fujivibe.recipe.tagLabel
import com.fujivibe.recipe.tagsBy
import com.fujivibe.ui.theme.Amber
import com.fujivibe.ui.theme.Ink
import com.fujivibe.ui.theme.OnAmber
import com.fujivibe.ui.theme.Paper
import com.fujivibe.ui.theme.PaperMuted

/**
 * "What are you shooting?": tick subjects, light and mood, and the app fills the slots that are not locked.
 * The ticks are remembered for next time.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SuggestSheet(model: CameraRecipesModel, onDismiss: () -> Unit) {
    val store = model.suggestStore
    var subjects by remember { mutableStateOf(store.subjects) }
    var light by remember { mutableStateOf(store.light) }
    var mood by remember { mutableStateOf(store.mood) }
    var mono by remember { mutableStateOf(store.includeMonochrome) }
    var filter by remember { mutableStateOf(store.hasFilter) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SheetColor,
        contentColor = Paper,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("What are you shooting?", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text("Tick what applies. Locked slots are left alone, and nothing goes to the camera until you load.", color = PaperMuted, fontSize = 13.sp)

            TagGroup("Subjects", model.recipes.tagsBy { it.bestFor.subjects }, subjects) { subjects = it }
            TagGroup("Light", model.recipes.tagsBy { it.bestFor.light }, light) { light = it }
            TagGroup("Mood", model.recipes.tagsBy { it.bestFor.mood }, mood) { mood = it }

            SwitchRow("Include a black and white recipe", mono) { mono = it }
            SwitchRow("I'm bringing a diffusion filter", filter) { filter = it }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Button(
                    onClick = {
                        model.suggest(subjects, light, mood, mono, filter)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber),
                ) { Text("Suggest") }
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                if (subjects.isNotEmpty() || light.isNotEmpty() || mood.isNotEmpty()) {
                    OutlinedButton(onClick = { subjects = emptySet(); light = emptySet(); mood = emptySet() }) { Text("Clear") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagGroup(title: String, tags: List<String>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                FilterChip(
                    selected = tag in selected,
                    onClick = { onChange(if (tag in selected) selected - tag else selected + tag) },
                    label = { Text(tagLabel(tag)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Amber,
                        selectedLabelColor = OnAmber,
                        labelColor = Paper,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = OnAmber, checkedTrackColor = Amber, uncheckedTrackColor = Ink),
        )
    }
}
