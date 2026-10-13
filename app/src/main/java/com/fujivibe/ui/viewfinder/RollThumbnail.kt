package com.fujivibe.ui.viewfinder

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fujivibe.capture.CaptureStore
import com.fujivibe.ui.theme.Amber
import com.fujivibe.ui.theme.OnAmber
import com.fujivibe.ui.theme.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The newest Capture on the Roll, with a count badge: the way into the shots still waiting to be
 * developed. It sits beside the shutter, so the next shot is never more than a tap away.
 */
@Composable
fun RollThumbnail(
    captureStore: CaptureStore,
    latestId: Long,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var thumbnail by remember(latestId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(latestId) {
        thumbnail = withContext(Dispatchers.IO) {
            captureStore.open(latestId).loadPreview(ThumbnailMaxDimension)?.asImageBitmap()
        }
    }

    Box(modifier) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PanelBackground)
                .border(2.dp, Paper, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick),
        ) {
            thumbnail?.let {
                Image(
                    bitmap = it,
                    contentDescription = "$count shots waiting to be developed",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp),
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 6.dp, y = (-6).dp)
                .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                .clip(CircleShape)
                .background(Amber)
                .padding(horizontal = 5.dp),
        ) {
            Text("$count", color = OnAmber, fontSize = 12.sp)
        }
    }
}

private const val ThumbnailMaxDimension = 160
