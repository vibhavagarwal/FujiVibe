package com.fujivibe.ui.roll

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fujivibe.R
import com.fujivibe.capture.CaptureStore
import com.fujivibe.capture.RollPolicy
import com.fujivibe.ui.theme.Paper
import com.fujivibe.ui.theme.PaperMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ThumbnailMaxDimension = 320

/** A Capture this close to expiring says so on its tile. */
private const val ExpiryWarningDays = 7

/**
 * The Roll: the Captures still waiting to be developed, newest first. Tapping one opens it in
 * Review.
 */
@Composable
fun RollScreen(
    captureStore: CaptureStore,
    ids: List<Long>,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    // Developing the last shot empties the Roll; there is nothing left to show, so leave.
    if (ids.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val now = System.currentTimeMillis()
    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onBack),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    tint = Paper,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text("Roll", color = Paper, fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
            Box(Modifier.weight(1f))
            Text(
                "${ids.size} of ${RollPolicy.MAX_CAPTURES}",
                color = PaperMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            "Shots wait here to be developed. They are kept for 30 days.",
            color = PaperMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(ids, key = { it }) { id ->
                CaptureTile(captureStore, id, daysLeft = RollPolicy.daysLeft(id, now), onClick = { onOpen(id) })
            }
        }
    }
}

@Composable
private fun CaptureTile(captureStore: CaptureStore, id: Long, daysLeft: Int, onClick: () -> Unit) {
    var thumbnail by remember(id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(id) {
        thumbnail = withContext(Dispatchers.IO) {
            captureStore.open(id).loadPreview(ThumbnailMaxDimension)?.asImageBitmap()
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF1E1E1E))
            .clickable(onClick = onClick),
    ) {
        thumbnail?.let {
            Image(
                bitmap = it,
                contentDescription = "Shot waiting to be developed",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Only when it matters; a fresh shot has weeks left and doesn't need the label.
        if (daysLeft <= ExpiryWarningDays) {
            Text(
                if (daysLeft == 0) "Expires today" else "${daysLeft}d left",
                color = Paper,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.85f), Offset(0f, 1f), blurRadius = 6f)),
                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 7.dp, vertical = 5.dp),
            )
        }
    }
}
