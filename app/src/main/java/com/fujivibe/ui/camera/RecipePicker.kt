package com.fujivibe.ui.camera

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fujivibe.recipe.Recipe
import com.fujivibe.recipe.RecipeFilter
import com.fujivibe.recipe.characterChips
import com.fujivibe.recipe.clarityNote
import com.fujivibe.recipe.settingLines
import com.fujivibe.recipe.tagLabel
import com.fujivibe.recipe.tagsBy
import com.fujivibe.ui.ScreenHeader
import com.fujivibe.ui.theme.Amber
import com.fujivibe.ui.theme.OnAmber
import com.fujivibe.ui.theme.Paper
import com.fujivibe.ui.theme.PaperMuted

/**
 * Choosing the recipe for one slot: every recipe with its character at a glance, filters by what it suits,
 * and a sheet with the full story. [otherSlotOf] says which other slot already has a recipe.
 */
@Composable
fun RecipePickerScreen(
    slot: Int,
    recipes: List<Recipe>,
    chosen: Recipe?,
    otherSlotOf: (Recipe) -> Int?,
    onPick: (Recipe?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    var filter by remember { mutableStateOf(RecipeFilter()) }
    var showFilters by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Recipe?>(null) }
    val shown = recipes.filter(filter::matches)

    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenHeader("Recipe for C$slot", onBack) {
            TextButton(onClick = { showFilters = !showFilters }) {
                Text(if (filter.count > 0) "Filter (${filter.count})" else "Filter", color = Amber)
            }
        }
        if (showFilters) FilterPanel(recipes, filter, onChange = { filter = it })
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (chosen == null) Amber.copy(alpha = 0.18f) else Paper.copy(alpha = 0.09f))
                        .clickable { onPick(null) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Leave as it is", color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Keep whatever the camera has in C$slot.", color = PaperMuted, fontSize = 13.sp)
                    }
                    if (chosen == null) Text("Chosen", color = Amber, fontSize = 13.sp)
                }
            }
            item { Text("${shown.size} of ${recipes.size} recipes", color = PaperMuted, fontSize = 13.sp) }
            items(shown, key = { it.id }) { r ->
                RecipeCard(r, selected = chosen?.id == r.id, elsewhere = otherSlotOf(r), onOpen = { detail = r }, onUse = { onPick(r) })
            }
            if (shown.isEmpty()) item { Text("No recipe matches all of these. Clear a filter to see more.", color = PaperMuted, fontSize = 14.sp) }
        }
    }

    detail?.let { r ->
        RecipeDetailSheet(r, slot, selected = chosen?.id == r.id, onUse = { onPick(r) }, onDismiss = { detail = null })
    }
}

@Composable
private fun RecipeCard(r: Recipe, selected: Boolean, elsewhere: Int?, onOpen: () -> Unit, onUse: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) Amber.copy(alpha = 0.18f) else Paper.copy(alpha = 0.09f))
            .clickable(onClick = onOpen).padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(r.name, color = Paper, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text((listOf(r.baseSimulation.label) + r.characterChips()).joinToString(" · "), color = PaperMuted, fontSize = 13.sp)
            val line = r.userDescription?.takeIf { it.isNotBlank() } ?: r.recommendationText.substringBefore(". ").take(140)
            if (line.isNotEmpty()) Text(line, color = Paper.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 2)
            if (elsewhere != null) Text("Also chosen for C$elsewhere", color = Amber, fontSize = 12.sp)
        }
        if (selected) Text("Chosen", color = Amber, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 12.dp))
        else TextButton(onClick = onUse) { Text("Use", color = Amber) }
    }
}

@Composable
private fun FilterPanel(recipes: List<Recipe>, filter: RecipeFilter, onChange: (RecipeFilter) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChipGroup("Type") {
            Chip("Colour", filter.monochrome == false) { onChange(filter.copy(monochrome = if (filter.monochrome == false) null else false)) }
            Chip("B&W", filter.monochrome == true) { onChange(filter.copy(monochrome = if (filter.monochrome == true) null else true)) }
        }
        ChipGroup("Suits") {
            recipes.tagsBy { it.bestFor.subjects }.forEach { t ->
                Chip(tagLabel(t), t in filter.subjects) { onChange(filter.copy(subjects = filter.subjects.toggled(t))) }
            }
        }
        ChipGroup("Light") {
            recipes.tagsBy { it.bestFor.light }.forEach { t ->
                Chip(tagLabel(t), t in filter.light) { onChange(filter.copy(light = filter.light.toggled(t))) }
            }
        }
        ChipGroup("Mood") {
            recipes.tagsBy { it.bestFor.mood }.forEach { t ->
                Chip(tagLabel(t), t in filter.mood) { onChange(filter.copy(mood = filter.mood.toggled(t))) }
            }
        }
        if (filter.count > 0) TextButton(onClick = { onChange(RecipeFilter()) }) { Text("Clear filters", color = Amber) }
    }
}

private fun Set<String>.toggled(tag: String) = if (tag in this) this - tag else this + tag

@Composable
private fun ChipGroup(label: String, chips: @Composable () -> Unit) {
    Column {
        Text(label, color = PaperMuted, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Amber,
            selectedLabelColor = OnAmber,
            labelColor = Paper,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeDetailSheet(r: Recipe, slot: Int, selected: Boolean, onUse: () -> Unit, onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
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
            Column {
                Text(r.name, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text((listOf(r.baseSimulation.label) + r.characterChips()).joinToString(" · "), color = PaperMuted, fontSize = 14.sp)
            }
            r.userDescription?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 15.sp) }
            Button(
                onClick = onUse,
                enabled = !selected,
                colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber),
            ) { Text(if (selected) "Chosen for C$slot" else "Use in C$slot") }

            if (r.recommendationText.isNotBlank()) Section("About this look") { Text(r.recommendationText, fontSize = 14.sp) }
            val suits = (r.bestFor.subjects + r.bestFor.light + r.bestFor.mood).map(::tagLabel)
            if (suits.isNotEmpty()) Section("Suits") { Text(suits.joinToString(", "), fontSize = 14.sp) }
            if (r.avoid.isNotEmpty()) Section("Not so good for") { r.avoid.forEach { Text("• $it", fontSize = 14.sp) } }
            Section("Written to the camera") {
                r.settingLines().forEach { (label, value) ->
                    Row {
                        Text(label, color = PaperMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(value, fontSize = 14.sp)
                    }
                }
            }
            r.clarityNote()?.let { Section("Clarity and a filter") { Text(it, fontSize = 14.sp) } }
            val advice = listOfNotNull(
                r.shootingNotes.iso.takeIf { it.isNotBlank() }?.let { "ISO: $it" },
                r.shootingNotes.exposureCompensation.takeIf { it.isNotBlank() }?.let { "Exposure compensation: $it" },
            )
            if (advice.isNotEmpty()) {
                Section("Shooting advice (set on the camera; the app cannot write these)") { advice.forEach { Text(it, fontSize = 14.sp) } }
            }
            val credit = listOf(r.source.author, r.source.publisher).filter { it.isNotBlank() }.joinToString(", ")
            if (credit.isNotEmpty()) {
                val url = r.source.url
                Text(
                    "Source: $credit",
                    color = if (url != null) Amber else PaperMuted,
                    fontSize = 13.sp,
                    modifier = if (url != null) Modifier.clickable { runCatching { uri.openUri(url) } } else Modifier,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        content()
    }
}
