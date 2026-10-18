package com.fujivibe.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fujivibe.R
import com.fujivibe.ui.ScreenHeader
import com.fujivibe.ui.theme.Amber
import com.fujivibe.ui.theme.Ink
import com.fujivibe.ui.theme.OnAmber
import com.fujivibe.ui.theme.Paper
import com.fujivibe.ui.theme.PaperMuted
import com.fujivibe.viewfinder.AspectChoice
import com.fujivibe.viewfinder.ViewfinderSettings

/**
 * Set-once choices for the Viewfinder (grid, level line, aspect ratio), saved as soon as they change,
 * and the way to the X-T5's camera recipes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenCameraRecipes: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val settings = remember { ViewfinderSettings(context) }
    var grid by remember { mutableStateOf(settings.gridOn) }
    var level by remember { mutableStateOf(settings.levelOn) }
    var aspect by remember { mutableStateOf(settings.aspect) }

    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenHeader("Settings", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SectionLabel("Viewfinder")
            SwitchRow("Grid lines", "Rule-of-thirds lines over the preview", grid) {
                grid = it
                settings.gridOn = it
            }
            SwitchRow("Level line", "A line that follows the horizon", level) {
                level = it
                settings.levelOn = it
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aspect ratio", color = Paper, fontSize = 16.sp)
                Text("The shape of every photo. 4:3 is the sensor's own; 3:2 is the shape of the X-T5's photos.", color = PaperMuted, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AspectChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = aspect == choice,
                            onClick = {
                                aspect = choice
                                settings.aspect = choice
                            },
                            label = { Text(choice.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Amber,
                                selectedLabelColor = OnAmber,
                                labelColor = Paper,
                            ),
                        )
                    }
                }
            }

            SectionLabel("Camera")
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenCameraRecipes).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Camera recipes", color = Paper, fontSize = 16.sp)
                    Text("Load recipes into the X-T5's custom slots over USB", color = PaperMuted, fontSize = 13.sp)
                }
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = PaperMuted, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, color = Amber, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Paper, fontSize = 16.sp)
            Text(detail, color = PaperMuted, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = OnAmber, checkedTrackColor = Amber, uncheckedTrackColor = Ink),
        )
    }
}
