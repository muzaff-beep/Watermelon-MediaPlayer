package com.watermelon.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.watermelon.common.model.MediaItem
import com.watermelon.ui.viewmodel.VideoListViewModel

/**
 * Lists the videos inside a single folder. Tapping a row invokes [onVideoClick] with the
 * selected [MediaItem]; the host typically navigates to the player route.
 */
@Composable
fun VideoListScreen(
    viewModel: VideoListViewModel,
    onVideoClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()

    if (videos.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Indexing videos…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(videos, key = { it.uri }) { item ->
            VideoRow(item = item, onClick = { onVideoClick(item) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun VideoRow(item: MediaItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = item.displayName.ifEmpty { item.uri.substringAfterLast('/') },
            style = MaterialTheme.typography.bodyLarge
        )
        val meta = buildString {
            if (item.durationMs > 0) {
                val totalSec = item.durationMs / 1000
                append("%d:%02d".format(totalSec / 60, totalSec % 60))
            }
            if (item.width > 0 && item.height > 0) {
                if (isNotEmpty()) append(" · ")
                append("${item.width}×${item.height}")
            }
            if (item.fileSize > 0) {
                if (isNotEmpty()) append(" · ")
                append("${item.fileSize / (1024 * 1024)} MB")
            }
        }
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
